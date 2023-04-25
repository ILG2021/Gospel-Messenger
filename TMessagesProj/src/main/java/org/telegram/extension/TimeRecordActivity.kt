package org.telegram.extension

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.blankj.utilcode.util.*
import com.blankj.utilcode.util.SizeUtils.dp2px
import kotlinx.android.synthetic.main.item_time_record.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import org.telegram.messenger.*
import org.telegram.tgnet.TLRPC
import org.telegram.ui.ActionBar.ActionBar.ActionBarMenuOnItemClick
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BackDrawable
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.AvatarDrawable
import org.telegram.ui.Components.RecyclerListView
import java.text.SimpleDateFormat
import java.util.*

class TimeRecordActivity(bundle: Bundle) : BaseFragment(bundle),
        NotificationCenter.NotificationCenterDelegate, CoroutineScope by MainScope() {
    private val timeRecords: MutableList<TimeRecord> = ArrayList()
    private lateinit var sp: SPUtils
    private val export = 11
    private val copy = 12
    private val delete = 13
    private var chatId = -1L

    private val refreshRunnable: Runnable = object : Runnable {
        override fun run() {
            refreshTimeRecords()
            if (fragmentView is RecyclerListView)
                (fragmentView as RecyclerListView).adapter?.notifyDataSetChanged()
            ThreadUtils.runOnUiThreadDelayed(this, 60 * 1000)
        }
    }

    override fun createView(context: Context): View {
        chatId = getArguments().getLong("chatId")
        sp = SPUtils.getInstance("TimeRecord_" + chatId + "_" + UserConfig.selectedAccount)

        actionBar.setTitle(
                LocaleController.getString(
                        "TimeRecord",
                        R.string.TimeRecord
                )
        )
        actionBar.setBackButtonDrawable(BackDrawable(false))
        actionBar.setActionBarMenuOnItemClick(object : ActionBarMenuOnItemClick() {
            override fun onItemClick(id: Int) {
                if (id == -1) {
                    finishFragment()
                } else if (id == export) {
                    val builder = AlertDialog.Builder(parentActivity)
                    builder.setTitle(
                            LocaleController.getString(
                                    "TimeRecordExport",
                                    R.string.TimeRecordExport
                            )
                    )

                    val linearLayout = LinearLayout(context)
                    linearLayout.orientation = LinearLayout.VERTICAL

                    val sheetUrl = SPUtils.getInstance("FillSheet").getString(
                            chatId.toString() + "_" + UserConfig.selectedAccount
                    )
                    val editText = EditText(context)
                    editText.setText(sheetUrl)
                    editText.textSize = 16f
                    editText.hint = LocaleController.getString(
                            "TimeRecordWriteSheetHint",
                            R.string.TimeRecordWriteSheetHint
                    )
                    editText.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint))
                    editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack))
                    editText.setBackgroundDrawable(Theme.createEditTextDrawable(context, false))
                    linearLayout.addView(editText, MATCH_PARENT, WRAP_CONTENT)
                    linearLayout.addView(TextView(context).apply {
                        text = LocaleController.getString(
                                "TimeRecordWriteSheetWarning",
                                R.string.TimeRecordWriteSheetPrompt
                        )
                        textSize = 14f
                        setTextColor(Theme.getColor(Theme.key_dialogTextGray))
                    }, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = dp2px(8f)
                    })
                    val dp24 = dp2px(24f)
                    linearLayout.setPadding(dp24, 0, dp24, 0)
                    builder.setView(linearLayout)
                    builder.setPositiveButton(
                            LocaleController.getString("OK", R.string.OK)
                    ) { _, _ ->
                        launch {
                            SPUtils.getInstance("FillSheet").put(
                                    chatId.toString() + "_" + UserConfig.selectedAccount,
                                    editText.text.toString()
                            )

                            val progressDialog = AlertDialog(parentActivity, 3)
                            progressDialog.show()

                            var state = 0           // Use state machine to parse questions
                            val questionsMap = HashMap<Long, HashMap<Int, ArrayList<String>>>()
                            val messages = (async(Dispatchers.IO) {
                                TimeRecordUtil.getMessagesForTimeRecord(chatId)
                            }).await()

                            val qMessages = HashMap<Int, String>()
                            val admins = getAdmins()
                            messages.forEach {
                                var userId = it.from_id.user_id
                                if (userId == 0L) userId = getUserConfig().getClientUserId()
                                if (isQuestion(admins, userId, it)) {
                                    state++
                                    qMessages[state] = it.message
                                    return@forEach
                                }

                                if (state > 0) {
                                    var stateQuestions = questionsMap[userId]
                                    if (stateQuestions == null)
                                        stateQuestions = HashMap()
                                    var questions = stateQuestions[state]
                                    if (questions == null)
                                        questions = ArrayList()
                                    questions.add(it.message)
                                    stateQuestions[state] = questions
                                    questionsMap[userId] = stateQuestions
                                }
                            }

                            val headers = mutableListOf(
                                    LocaleController.getString(
                                            "TimeRecordAvatar",
                                            R.string.TimeRecordAvatar
                                    ),
                                    "ID",
                                    LocaleController.getString(
                                            "TimeRecordPhoneNo",
                                            R.string.TimeRecordPhoneNo
                                    ),
                                    LocaleController.getString(
                                            "TimeRecordName",
                                            R.string.TimeRecordName
                                    ),
                                    LocaleController.getString(
                                            "TimeRecordUserName",
                                            R.string.TimeRecordUserName
                                    ),
                                    LocaleController.getString(
                                            "TimeRecordDuration",
                                            R.string.TimeRecordDuration
                                    ),
                                    LocaleController.getString(
                                            "TimeRecordTimestamps",
                                            R.string.TimeRecordTimestamps
                                    ),
                                    LocaleController.getString("TimeRecordDate", R.string.TimeRecordDate)
                            )
                            if (state > 0) {
                                for (i in 1..state)
                                    headers.add("Q$i\n${qMessages[i]}")
                            }

                            val values = ArrayList<List<String>>()
                            values.add(headers)

                            val avatarTasks = ArrayList<Flow<String>>()
                            timeRecords.forEach {
                                val row = baseRow(it.userId, avatarTasks)
                                row.add(it.duration)
                                row.add(it.getTimestampsDetail())
                                row.add(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                                if (state > 0) {
                                    for (i in 1..state) {
                                        row.add(
                                                questionsMap[it.userId]?.get(i)?.joinToString("\n")
                                                        ?: ""
                                        )
                                    }
                                    questionsMap.remove(it.userId)
                                }
                                values.add(row)
                            }

                            questionsMap.keys.forEach {
                                if (it == AccountInstance.getInstance(currentAccount).userConfig.getClientUserId())
                                    return@forEach
                                val stateQuestions = questionsMap[it]
                                val row = baseRow(it, avatarTasks)
                                row.add("")
                                row.add("")
                                row.add(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()))
                                if (state > 0) {
                                    for (i in 1..state) {
                                        row.add(stateQuestions?.get(i)?.joinToString("\n") ?: "")
                                    }
                                }
                                values.add(row)
                            }

                            val firstIndexList = values.drop(1).map { it[1] }
                            val needCollect = ArrayList(values.drop(1).map { it[1] })
                            avatarTasks.merge().collect {
                                val json = JSONObject(it)
                                val userId = json.getString("userId")
                                val data = json.getString("data")
                                if (!data.isNullOrBlank())
                                    values[firstIndexList.indexOf(userId) + 1] =
                                            ArrayList<String>(values[firstIndexList.indexOf(userId) + 1]).apply {
                                                set(0, data)
                                            }
                                needCollect.remove(userId)
                                if (needCollect.isEmpty() && progressDialog.isShowing) {
                                    (async(Dispatchers.IO) {
                                        SheetUtil.write(editText.text.toString(), values)
                                    }).await()
                                    progressDialog.dismiss()
                                }
                            }
                        }
                    }
                    showDialog(builder.create(), true, null)
                } else if (id == delete) {
                    sp.clear()
                    TimeRecordUtil.timeHoleSp(chatId).clear()
                    ToastUtils.showShort(
                            LocaleController.getString(
                                    "TimeRecordClearDone",
                                    R.string.TimeRecordClearDone
                            )
                    )

                    finishFragment()
                } else if (id == copy) {
                    AndroidUtilities.addToClipboard(GsonUtils.toJson(timeRecords))
                    ToastUtils.showShort(
                            LocaleController.getString(
                                    "TextCopied",
                                    R.string.TextCopied
                            )
                    )
                }
            }

            private fun getAdmins(): List<Long> {
                val admins = mutableListOf<Long>()
                val chat = messagesController.getChatFull(chatId)
                chat.participants.participants.forEach {
                    if (it is TLRPC.TL_chatParticipantAdmin || it is TLRPC.TL_chatParticipantCreator)
                        admins.add(it.user_id)
                }

                val adminArray = messagesController.channelAdmins[chatId]
                if (adminArray != null) {
                    for (i in 0 until adminArray.size())
                        admins.add(adminArray.valueAt(i).peer.user_id)
                }
                return admins
            }

            private fun baseRow(
                    userId: Long,
                    avatarTasks: ArrayList<Flow<String>>
            ): ArrayList<String> {
                val row = ArrayList<String>()
                val user = messagesController.getUser(userId)
                row.add("")
                row.add(user.id.toString())
                avatarTasks.add(avatarTask(user))
                row.add(user.phone)
                row.add(
                        ContactsController.formatName(
                                user.first_name,
                                user.last_name
                        )
                )
                if (user.username.isNullOrBlank())
                    row.add("")
                else
                    row.add("@" + user.username)
                return row
            }

            private fun avatarTask(user: TLRPC.User) = callbackFlow {
                val imageReceiver = ImageReceiver()
                imageReceiver.setDelegate { imageReceiver1, set, thumb, memCache ->
                    if (imageReceiver1.imageLocation == null || set) {
                        val json = JSONObject()
                        json.put("userId", user.id)
                        if (imageReceiver1.drawable is BitmapDrawable) {
                            val bitmap =
                                    (imageReceiver1.drawable as BitmapDrawable).bitmap
                            json.put(
                                    "data",
                                    "data:image/jpg;base64," + EncodeUtils.base64Encode2String(
                                            ImageUtils.bitmap2Bytes(
                                                    ImageUtils.scale(
                                                            bitmap,
                                                            50,
                                                            50
                                                    )
                                            )
                                    )
                            )
                            trySend(json.toString())
                        } else {
                            json.put("data", "")
                            trySend(json.toString())
                        }
                    }
                }

                imageReceiver.setImage(
                        ImageLocation.getForUser(
                                user,
                                ImageLocation.TYPE_SMALL
                        ),
                        "50_50",
                        null,
                        null,
                        0,
                        null,
                        user,
                        0
                )

                awaitClose {
                    imageReceiver.setDelegate(null)
                }
            }

            private fun isQuestion(admins: List<Long>, userId: Long, message: TLRPC.Message): Boolean {
                return !TextUtils.isEmpty(message.message) && message.message.length >= 30 &&
                        (message.message.startsWith("Q") || message.message.startsWith("Pregunta")) &&
                        admins.contains(userId)
            }
        })

        val menu = actionBar.createMenu()
        val otherItem = menu.addItem(10, R.drawable.ic_ab_other)
        otherItem.addSubItem(
                export,
                R.drawable.msg_share,
                LocaleController.getString("TimeRecordExportToSheet", R.string.TimeRecordExportToSheet)
        )
        otherItem.addSubItem(
                copy,
                R.drawable.msg_copy,
                LocaleController.getString("Copy", R.string.Copy)
        )
        otherItem.addSubItem(
                delete,
                R.drawable.msg_delete,
                LocaleController.getString("TimeRecordClear", R.string.TimeRecordClear)
        )

        refreshTimeRecords()
        val recyclerListView = RecyclerListView(context)
        fragmentView = recyclerListView
        recyclerListView.layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        recyclerListView.adapter = object : RecyclerView.Adapter<BaseViewHolder>() {
            override fun onCreateViewHolder(
                    parent: ViewGroup,
                    viewType: Int
            ): BaseViewHolder {
                val vh = BaseViewHolder(
                        LayoutInflater.from(context).inflate(R.layout.item_time_record, parent, false)
                )
                vh.avatar.setRoundRadius(dp2px(24f))
                vh.avatar.tag = AvatarDrawable()
                return vh
            }

            @SuppressLint("SetTextI18n")
            override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
                try {
                    val timeRecord = timeRecords[position]
                    holder.name.text = timeRecord.name
                    holder.name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText))
                    holder.duration.text = LocaleController.getString(
                            "TimeRecordTotalDuration",
                            R.string.TimeRecordTotalDuration
                    ) + timeRecord.duration
                    holder.duration.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText))
                    holder.steps.text = timeRecord.getTimestampsDetail()
                    holder.steps.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText))
                    val user = messagesController.getUser(timeRecord.userId)
                    val drawable = holder.avatar.tag as AvatarDrawable
                    drawable.setInfo(user)
                    holder.avatar.setImage(
                            ImageLocation.getForUser(
                                    user,
                                    ImageLocation.TYPE_SMALL
                            ), "50_50", drawable, user
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            override fun getItemCount(): Int {
                return timeRecords.size
            }
        }

        accountInstance.notificationCenter.addObserver(this, NotificationCenter.groupCallUpdated)
        ThreadUtils.getMainHandler().postDelayed(refreshRunnable, 60 * 1000)
        return fragmentView
    }

    override fun onFragmentDestroy() {
        ThreadUtils.getMainHandler().removeCallbacks(refreshRunnable)
        accountInstance.notificationCenter.removeObserver(this, NotificationCenter.groupCallUpdated)
        cancel()
        super.onFragmentDestroy()
    }

    private fun refreshTimeRecords() {
        timeRecords.clear()
        sp.all.forEach { (key, value) ->
            try {
                val record = GsonUtils.fromJson(value.toString(), TimeRecord::class.java)
                val user = messagesController.getUser(key.toLong())
                record.userId = user.id
                record.name = UserObject.getUserName(user)
                timeRecords.add(record)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun didReceivedNotification(id: Int, account: Int, vararg args: Any?) {
        refreshTimeRecords()
        if (fragmentView is RecyclerListView)
            (fragmentView as RecyclerListView).adapter?.notifyDataSetChanged()
    }

    fun TimeRecord.getTimestampsDetail(): String {
        return LocaleController.getString("Online", R.string.Online) +
                ":" + onlines.joinToString(" ") {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(it))
        } + "\n" +
                LocaleController.getString("VoipOfflineTitle", R.string.VoipOfflineTitle) +
                ":" + offlines.joinToString(" ") {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(it))
        }
    }
}