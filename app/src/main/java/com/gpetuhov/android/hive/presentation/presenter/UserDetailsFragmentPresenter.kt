package com.gpetuhov.android.hive.presentation.presenter

import com.arellomobile.mvp.InjectViewState
import com.arellomobile.mvp.MvpPresenter
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.domain.interactor.FavoritesInteractor
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.managers.LocationMapManager
import com.gpetuhov.android.hive.presentation.view.UserDetailsFragmentView
import javax.inject.Inject

@InjectViewState
class UserDetailsFragmentPresenter : MvpPresenter<UserDetailsFragmentView>(), FavoritesInteractor.Callback {

    @Inject lateinit var repo: Repo
    @Inject lateinit var locationMapManager: LocationMapManager

    var userUid = ""
    var userIsFavorite = false

    private var favoritesInteractor = FavoritesInteractor(this)

    init {
        HiveApp.appComponent.inject(this)
    }

    // === FavoritesInteractor.Callback ===

    override fun onFavoritesError(errorMessage: String) = viewState.showToast(errorMessage)

    // === Public methods ===

    fun navigateUp() = viewState.navigateUp()

    fun openChat() {
        repo.clearMessages()
        viewState.openChat()
    }

    fun openOffer(offerUid: String) = viewState.openOffer(offerUid)

    fun openPhotos(photoUrlList: MutableList<String>) = viewState.openPhotos(photoUrlList)

    fun openLocation() {
        locationMapManager.resetMapState()  // This is needed to move camera to location
        viewState.openLocation(userUid)
    }

    // Sequence:
    // 1. Interactor forces repo to update favorites list (add or remove user from favorites)
    // 2. On favorite list change, second user is updates (favorites status is changed)
    // 3. On second user change, view model forces UI to change
    fun favorite() = favoritesInteractor.favorite(userIsFavorite, userUid, "")

    fun favoriteOffer(offerIsFavorite: Boolean, offerUid: String) = favoritesInteractor.favorite(offerIsFavorite, userUid, offerUid)

    // --- Lifecycle ---

    // This is needed to change user details in the UI if changed on the backend
    fun onResume() = repo.startGettingSecondUserUpdates(userUid)

    fun onPause() = repo.stopGettingSecondUserUpdates()
}