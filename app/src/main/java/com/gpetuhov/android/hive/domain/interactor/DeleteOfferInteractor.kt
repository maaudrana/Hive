package com.gpetuhov.android.hive.domain.interactor

import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.interactor.base.Interactor
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.domain.util.ResultMessages
import javax.inject.Inject

class DeleteOfferInteractor(private val callback: Callback) : Interactor {

    interface Callback {
        fun onDeleteOfferSuccess()
        fun onDeleteOfferError(errorMessage: String)
    }

    @Inject lateinit var repo: Repo
    @Inject lateinit var resultMessages: ResultMessages

    private var offerUid = ""

    init {
        HiveApp.appComponent.inject(this)
    }

    // Do not call this directly!
    override fun execute() {
        repo.deleteOffer(
            offerUid,
            { callback.onDeleteOfferSuccess() },
            { callback.onDeleteOfferError(resultMessages.getDeleteOfferErrorMessage()) }
        )
    }

    // Call this method to delete offer
    fun deleteOffer(offerUid: String) {
        this.offerUid = offerUid
        execute()
    }
}