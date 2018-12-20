package com.gpetuhov.android.hive.domain.model

// This is what the user offers to the world
data class Offer(
    var uid: String,
    var userUid: String,
    var title: String,
    var description: String,
    var price: Double,
    var isFree: Boolean,
    var isActive: Boolean,
    var isFavorite: Boolean = false,
    var rating: Double = 0.0
) {
    var photoList = mutableListOf<Photo>()
}