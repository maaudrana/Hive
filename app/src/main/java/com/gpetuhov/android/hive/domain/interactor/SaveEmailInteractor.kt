package com.gpetuhov.android.hive.domain.interactor

import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.domain.util.ResultMessages
import javax.inject.Inject

class SaveEmailInteractor(private val callback: Callback) : Interactor {

    interface Callback {
        fun onSaveEmailError(errorMessage: String)
    }

    @Inject lateinit var repo: Repo
    @Inject lateinit var resultMessages: ResultMessages

    private var newEmail = ""

    init {
        HiveApp.appComponent.inject(this)
    }

    // Do not call this directly!
    override fun execute() {
        repo.saveUserVisibleEmail(newEmail) { callback.onSaveEmailError(resultMessages.getSaveEmailErrorMessage()) }
    }

    // Call this method to save new email
    fun saveEmail(newEmail: String) {
        this.newEmail = newEmail
        execute()
    }
}