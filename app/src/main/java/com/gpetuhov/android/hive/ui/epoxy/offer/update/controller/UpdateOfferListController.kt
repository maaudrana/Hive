package com.gpetuhov.android.hive.ui.epoxy.offer.update.controller

import android.content.Context
import com.gpetuhov.android.hive.R
import com.gpetuhov.android.hive.application.HiveApp
import com.gpetuhov.android.hive.presentation.presenter.UpdateOfferFragmentPresenter
import com.gpetuhov.android.hive.ui.epoxy.base.controller.BaseController
import com.gpetuhov.android.hive.ui.epoxy.offer.update.models.updateOfferDetails
import com.gpetuhov.android.hive.ui.epoxy.offer.update.models.updateOfferPrice
import com.gpetuhov.android.hive.ui.epoxy.offer.update.models.updateOfferReviews
import com.gpetuhov.android.hive.ui.epoxy.profile.models.addPhoto
import com.gpetuhov.android.hive.util.Constants
import com.gpetuhov.android.hive.util.Settings
import javax.inject.Inject

class UpdateOfferListController(private val presenter: UpdateOfferFragmentPresenter) : BaseController() {

    @Inject lateinit var context: Context
    @Inject lateinit var settings: Settings

    init {
        HiveApp.appComponent.inject(this)
    }

    override fun buildModels() {
        val photoList = presenter.photoList.filter { !it.isDeleted }.toMutableList()

        addPhoto {
            id("addPhoto")
            onClick { presenter.choosePhoto() }
            maxPhotoWarningVisible(photoList.size >= Constants.Offer.MAX_VISIBLE_PHOTO_COUNT)
        }

        photoCarousel(
            settings,
            photoList,
            false,
            false,
            { photoUrlList -> presenter.openPhotos(photoUrlList) },
            { photoUid -> presenter.showDeletePhotoDialog(photoUid) }
        )

        updateOfferDetails {
            id("update_offer_details")

            active(presenter.active)
            activeEnabled(presenter.activeEnabled)
            onActiveClick { isActive -> presenter.onActiveClick(isActive) }

            maxActiveWarningVisible(!presenter.activeEnabled)

            title(if (presenter.title != "") presenter.title else context.getString(R.string.add_title))
            onTitleClick { presenter.showTitleDialog() }

            description(if (presenter.description != "") presenter.description else context.getString(R.string.add_description))
            onDescriptionClick { presenter.showDescriptionDialog() }
        }

        updateOfferPrice {
            id("update_offer_price")

            free(presenter.free)
            onFreeClick { isFree -> presenter.onFreeClick(isFree) }

            price(presenter.price.toString())
            onPriceClick { presenter.showPriceDialog() }
        }

        updateOfferReviews {
            id("update_offer_reviews")

            val reviewCount = presenter.reviewCount
            val noReviews = reviewCount == 0
            val allReviews = context.getString(R.string.all_reviews)
            reviewsActionText(if (noReviews) context.getString(R.string.no_reviews2) else "$allReviews ($reviewCount)")

            rating(presenter.rating)

            onClick { if (!noReviews) presenter.openReviews() }
        }
    }
}