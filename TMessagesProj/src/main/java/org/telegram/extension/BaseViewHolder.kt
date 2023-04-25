package org.telegram.extension

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.extensions.CacheImplementation
import kotlinx.android.extensions.ContainerOptions
import kotlinx.android.extensions.LayoutContainer

@ContainerOptions(CacheImplementation.SPARSE_ARRAY)
open class BaseViewHolder(
    override val containerView: View
) : RecyclerView.ViewHolder(
    containerView
), LayoutContainer