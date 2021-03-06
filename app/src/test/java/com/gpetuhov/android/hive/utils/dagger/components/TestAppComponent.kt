package com.gpetuhov.android.hive.utils.dagger.components

import com.gpetuhov.android.hive.application.dagger.components.AppComponent
import com.gpetuhov.android.hive.interactors.*
import com.gpetuhov.android.hive.utils.dagger.modules.TestAppModule
import dagger.Component
import javax.inject.Singleton

// This Dagger's component is used in tests instead of AppComponent

@Component(modules = [TestAppModule::class])
@Singleton
interface TestAppComponent : AppComponent {
    fun inject(deleteUserInteractorTest: DeleteUserInteractorTest)
    fun inject(signOutInteractorTest: SignOutInteractorTest)
    fun inject(saveUsernameInteractorTest: SaveUsernameInteractorTest)
    fun inject(saveDescriptionInteractorTest: SaveDescriptionInteractorTest)
    fun inject(searchInteractorTest: SearchInteractorTest)
    fun inject(sendMessageInteractorTest: SendMessageInteractorTest)
    fun inject(saveOfferInteractorTest: SaveOfferInteractorTest)
    fun inject(deleteOfferInteractorTest: DeleteOfferInteractorTest)
    fun inject(deleteUserPhotoInteractorTest: DeleteUserPhotoInteractorTest)
    fun inject(favoritesInteractorTest: FavoritesInteractorTest)
    fun inject(saveReviewInteractorTest: SaveReviewInteractorTest)
    fun inject(deleteReviewInteractorTest: DeleteReviewInteractorTest)
    fun inject(saveCommentInteractorTest: SaveCommentInteractorTest)
    fun inject(deleteCommentInteractorTest: DeleteCommentInteractorTest)
    fun inject(savePhoneInteractorTest: SavePhoneInteractorTest)
    fun inject(saveEmailInteractorTest: SaveEmailInteractorTest)
    fun inject(saveSkypeInteractorTest: SaveSkypeInteractorTest)
    fun inject(saveFacebookInteractorTest: SaveFacebookInteractorTest)
    fun inject(saveTwitterInteractorTest: SaveTwitterInteractorTest)
    fun inject(saveInstagramInteractorTest: SaveInstagramInteractorTest)
    fun inject(saveYouTubeInteractorTest: SaveYouTubeInteractorTest)
    fun inject(saveWeblinkInteractorTest: SaveWebsiteInteractorTest)
    fun inject(saveResidenceInteractorTest: SaveResidenceInteractorTest)
    fun inject(saveLanguageInteractorTest: SaveLanguageInteractorTest)
    fun inject(saveEducationInteractorTest: SaveEducationInteractorTest)
    fun inject(saveWorkInteractorTest: SaveWorkInteractorTest)
    fun inject(saveInterestsInteractorTest: SaveInterestsInteractorTest)
}
