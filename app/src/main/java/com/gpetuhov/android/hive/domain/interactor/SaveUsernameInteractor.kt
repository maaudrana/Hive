package com.gpetuhov.android.hive.domain.interactor

import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.domain.util.ResultMessages
import javax.inject.Inject

class SaveUsernameInteractor(private val callback: Callback) : Interactor {

    interface Callback {
        fun onSaveUsernameError(errorMessage: String)
    }

    @Inject lateinit var repo: Repo
    @Inject lateinit var resultMessages: ResultMessages

    private var newUsername = ""

    init {
        HiveApp.appComponent.inject(this)
    }

    // Do not call this directly!
    override fun execute() {
        repo.saveUserUsername(newUsername) { callback.onSaveUsernameError(resultMessages.getSaveUsernameErrorMessage()) }
    }

    // Call this method to save new username
    fun saveUsername(newUsername: String) {
        this.newUsername = newUsername
        execute()
    }
}