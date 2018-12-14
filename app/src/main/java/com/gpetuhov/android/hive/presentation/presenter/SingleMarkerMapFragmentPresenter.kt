package com.gpetuhov.android.hive.presentation.presenter

import com.arellomobile.mvp.InjectViewState
import com.arellomobile.mvp.MvpPresenter
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.presentation.view.SingleMarkerMapFragmentView
import javax.inject.Inject

@InjectViewState
class SingleMarkerMapFragmentPresenter : MvpPresenter<SingleMarkerMapFragmentView>() {

    @Inject lateinit var repo: Repo

    var userUid = ""

    init {
        HiveApp.appComponent.inject(this)
    }

    // === Public methods ===

    fun navigateUp() = viewState.navigateUp()

    // --- Lifecycle ---

    fun onResume() = repo.startGettingSecondUserUpdates(userUid)

    fun onPause() = repo.stopGettingSecondUserUpdates()
}