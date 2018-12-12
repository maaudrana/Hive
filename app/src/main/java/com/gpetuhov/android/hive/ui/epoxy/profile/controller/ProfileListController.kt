package com.gpetuhov.android.hive.ui.epoxy.profile.controller

import android.content.Context
import com.gpetuhov.android.hive.R
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.presentation.presenter.ProfileFragmentPresenter
import com.gpetuhov.android.hive.ui.epoxy.base.UserBaseController
import com.gpetuhov.android.hive.ui.epoxy.profile.models.addOffer
import com.gpetuhov.android.hive.ui.epoxy.profile.models.addPhoto
import com.gpetuhov.android.hive.ui.epoxy.profile.models.details
import com.gpetuhov.android.hive.ui.epoxy.profile.models.settings
import com.gpetuhov.android.hive.util.Constants
import com.gpetuhov.android.hive.util.Settings
import javax.inject.Inject

class ProfileListController(private val presenter: ProfileFragmentPresenter) : UserBaseController() {

    @Inject lateinit var context: Context
    @Inject lateinit var settings: Settings

    private var signOutEnabled = true
    private var deleteAccountEnabled = true

    init {
        HiveApp.appComponent.inject(this)
    }

    override fun buildModels() {
        val photoList = user?.photoList ?: mutableListOf()

        addPhoto {
            id("addPhoto")
            onClick { presenter.choosePhoto() }
            maxPhotoWarningVisible(photoList.size >= Constants.User.MAX_VISIBLE_PHOTO_COUNT)
        }

        photoCarousel(
            settings,
            photoList,
            false,
            true,
            { photoUrlList -> presenter.openPhotos(photoUrlList) },
            { photoUid -> presenter.showDeletePhotoDialog(photoUid) }
        )

        details {
            id("details")

            val hasUsername = user?.hasUsername ?: false
            username(if (hasUsername) user?.username ?: "" else context.getString(R.string.enter_username))
            onUsernameClick { presenter.showUsernameDialog() }

            userPicUrl(user?.userPicUrl ?: "")
            onUserPicClick { presenter.chooseUserPic() }

            name(user?.name ?: "")
            email(user?.email ?: "")

            val hasDescription = user?.hasDescription ?: false
            description(if (hasDescription) user?.description ?: "" else context.getString(R.string.enter_description))
            onDescriptionClick { presenter.showDescriptionDialog() }

            val hasActiveOffer = user?.hasActiveOffer() ?: false
            noActiveOffersWarningVisible(!hasActiveOffer)
        }

        user?.offerList?.forEach { offer ->
            userOfferItem(context, settings, offer, true) { presenter.updateOffer(offer.uid) }
        }

        addOffer {
            id("addOffer")
            onClick { presenter.updateOffer("") }
        }

        settings {
            id("settings")

            onSignOutClick { presenter.showSignOutDialog() }
            signOutEnabled(signOutEnabled)

            onDeleteAccountClick { presenter.showDeleteUserDialog() }
            deleteAccountEnabled(deleteAccountEnabled)
        }
    }

    fun signOutEnabled(isEnabled: Boolean) {
        signOutEnabled = isEnabled
        requestModelBuild()
    }

    fun deleteAccountEnabled(isEnabled: Boolean) {
        deleteAccountEnabled = isEnabled
        requestModelBuild()
    }
}