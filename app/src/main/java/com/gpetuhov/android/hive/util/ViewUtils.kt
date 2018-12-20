package com.gpetuhov.android.hive.util

import android.view.View
import com.gpetuhov.android.hive.R

fun getStarResourceId(favorite: Boolean) = if (favorite) R.drawable.ic_star else R.drawable.ic_star_border

fun getPriceColorId(free: Boolean) = if (free) R.color.md_red_600 else R.color.md_grey_600

fun View.setVisible(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}