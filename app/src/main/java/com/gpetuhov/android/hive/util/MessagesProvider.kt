package com.gpetuhov.android.hive.util

import android.content.Context
import com.gpetuhov.android.hive.R
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.util.Messages
import javax.inject.Inject

class MessagesProvider : Messages {

    @Inject lateinit var context: Context

    init {
        HiveApp.appComponent.inject(this)
    }

    override fun getSignOutErrorMessage(): String = getString(R.string.sign_out_error)

    override fun getSignOutNetworkErrorMessage(): String = getString(R.string.sign_out_no_network)

    private fun getString(stringId: Int) = context.getString(stringId)
}