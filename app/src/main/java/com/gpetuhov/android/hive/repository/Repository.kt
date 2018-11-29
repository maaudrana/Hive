package com.gpetuhov.android.hive.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.*
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.storage.FirebaseStorage
import com.gpetuhov.android.hive.domain.model.Chatroom
import com.gpetuhov.android.hive.domain.model.Message
import com.gpetuhov.android.hive.domain.model.User
import com.gpetuhov.android.hive.util.Constants
import timber.log.Timber
import java.util.*
import com.gpetuhov.android.hive.domain.repository.Repo
import com.gpetuhov.android.hive.managers.LocationManager
import org.imperiumlabs.geofirestore.GeoFirestore
import org.imperiumlabs.geofirestore.GeoQuery
import org.imperiumlabs.geofirestore.GeoQueryDataEventListener
import org.jetbrains.anko.defaultSharedPreferences
import java.lang.Exception
import android.graphics.Bitmap
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.firebase.storage.StorageReference
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import com.google.firebase.firestore.DocumentSnapshot
import com.gpetuhov.android.hive.domain.model.Offer
import kotlin.collections.HashMap

// Read and write data to remote storage (Firestore)
class Repository(private val context: Context) : Repo {

    companion object {
        private const val TAG = "Repo"

        // Collections
        private const val USERS_COLLECTION = "users"
        private const val CHATROOMS_COLLECTION = "chatrooms"
        private const val MESSAGES_COLLECTION = "messages"
        private const val USER_CHATROOMS_COLLECTION = "userChatrooms"
        private const val CHATROOMS_OF_USER_COLLECTION = "chatroomsOfUser"

        // User
        private const val NAME_KEY = "name"
        private const val USERNAME_KEY = "username"
        private const val EMAIL_KEY = "email"
        private const val DESCRIPTION_KEY = "description"
        private const val USER_PIC_URL_KEY = "userPicUrl"
        private const val OFFER_LIST_KEY = "offerList"
        private const val IS_ONLINE_KEY = "is_online"
        private const val LOCATION_KEY = "l"
        private const val FCM_TOKEN_KEY = "fcm_token"

        // Offer
        private const val OFFER_TITLE_KEY = "offer_title"
        private const val OFFER_DESCRIPTION_KEY = "offer_description"

        // Message
        private const val SENDER_UID_KEY = "sender_uid"
        private const val RECEIVER_UID_KEY = "receiver_uid"
        private const val TIMESTAMP_KEY = "timestamp"
        private const val MESSAGE_TEXT_KEY = "message_text"
        private const val MESSAGE_IS_READ_KEY = "isRead"

        // Chatroom
        private const val CHATROOM_USER_UID_1_KEY = "userUid1"
        private const val CHATROOM_USER_UID_2_KEY = "userUid2"
        private const val CHATROOM_USER_NAME_1_KEY = "userName1"
        private const val CHATROOM_USER_NAME_2_KEY = "userName2"
        private const val CHATROOM_USER_PIC_URL_1_KEY = "userPicUrl1"
        private const val CHATROOM_USER_PIC_URL_2_KEY = "userPicUrl2"
        private const val CHATROOM_LAST_MESSAGE_TEXT_KEY = "lastMessageText"
        private const val CHATROOM_LAST_MESSAGE_TIMESTAMP_KEY = "lastMessageTimestamp"
        private const val CHATROOM_NEW_MESSAGE_COUNT_KEY = "newMessageCount"

        // Shared Preferences
        private const val UNREAD_MESSAGES_EXIST_KEY = "unreadMessagesExist"

        // User pic
        private const val USER_PIC_SIZE = 100
    }

    // Firestore is the single source of truth for the currentUser property.
    // currentUser is updated every time we write data to the corresponding
    // Firestore document (with the uid of the current user).
    // Current user is wrapped inside LiveData
    // so that the UI can easily observe changes and update itself.
    // So the sequence of updates is:
    // 1. Write data to Firestore
    // 2. currentUser is updated
    // 3. UI that observes currentUser (through ViewModel) is updated
    private val currentUser = MutableLiveData<User>()

    // Second user in the chat and user from search results
    private val secondUser = MutableLiveData<User>()

    // For searchResult the single source of truth is also Firestore.
    // Sequence of updates:
    // 1. Data in Firestore is updated
    // 2. searchResult is updated
    // 3. UI the observes searchResult is updated
    // (Same sequence of changes is used for all other data, that Repository provides)
    private val searchResult = MutableLiveData<MutableMap<String, User>>()
    private val tempSearchResult = mutableMapOf<String, User>()

    // Messages of the current chatroom
    // (chatroom is the chat between current user and second user)
    private val messages = MutableLiveData<MutableList<Message>>()

    // Value is true if unread messages exist
    private val unreadMessagesFlag = MutableLiveData<Boolean>()

    // Chatrooms of the current user
    private val chatrooms = MutableLiveData<MutableList<Chatroom>>()

    private var isAppInForeground = false

    private var isUserInChatroomsList = false

    private var isUserInChatroom = false

    // True if current user is authorized
    private var isAuthorized = false

    // Query text used to search users on map
    private var queryText = ""

    // Uid of the current chatroom
    private var currentChatRoomUid = ""

    // Counts how many times chatroom list has been updated,
    // since we started listening to its changes.
    private var chatroomsUpdateCounter = 0

    // Firestore
    private val firestore = FirebaseFirestore.getInstance()

    // GeoFirestore is used to query users by location
    private var geoFirestore: GeoFirestore
    private var geoQuery: GeoQuery? = null

    private var storage = FirebaseStorage.getInstance()

    // Firestore listeners
    private var currentUserListenerRegistration: ListenerRegistration? = null
    private var secondUserListenerRegistration: ListenerRegistration? = null
    private var messagesListenerRegistration: ListenerRegistration? = null
    private var chatroomsListenerRegistration: ListenerRegistration? = null

    init {
        // Offline data caching is enabled by default in Android.
        // But we enable it explicitly to be sure.
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()

        firestore.firestoreSettings = settings

        geoFirestore = GeoFirestore(firestore.collection(USERS_COLLECTION))

        resetCurrentUser()
        resetSecondUser()
        clearResult()
        resetChatrooms()
        initUnreadMessagesFlag()
    }

    // === Repo ===
    // --- App status ---

    override fun isForeground() = isAppInForeground

    override fun setForeground(value: Boolean) {
        isAppInForeground = value
    }

    override fun isChatroomListOpen() = isUserInChatroomsList

    override fun setChatroomListOpen(value: Boolean) {
        isUserInChatroomsList = value
    }

    // Return true if chat with secondUserUid is open
    override fun isChatroomOpen(secondUserUid: String) = isUserInChatroom && secondUserUid == secondUserUid()

    override fun setChatroomOpen(value: Boolean) {
        isUserInChatroom = value
    }

    // --- Authentication ---

    override fun onSignIn(user: User) {
        if (!isAuthorized) {
            isAuthorized = true

            // Current user's uid initially comes from Firebase Auth,
            // so we must save it to start getting updates
            // for the corresponding documents in Firestore.
            val tempUser = createAnonymousUser()
            tempUser.uid = user.uid
            currentUser.value = tempUser
            startGettingCurrentUserUpdates()

            // Current user's name and email initially come from Firebase Auth,
            // so after successful sign in we must write them to Firestore.
            saveUserNameEmailPicAndToken(user)
        }
    }

    override fun onSignOut() {
        if (isAuthorized) {
            isAuthorized = false
            stopGettingCurrentUserUpdates()
            stopGettingSearchResultUpdates()
            resetCurrentUser()
        }
    }

    // --- User ---

    override fun currentUser() = currentUser

    override fun secondUser() = secondUser

    override fun currentUserUsername() = currentUser.value?.username ?: ""

    override fun currentUserDescription() = currentUser.value?.description ?: ""

    override fun saveUserUsername(newUsername: String, onError: () -> Unit) {
        val data = HashMap<String, Any>()
        data[USERNAME_KEY] = newUsername

        // Save user name.
        saveUserDataRemote(data, { /* Do nothing */ }, onError)
    }

    override fun saveUserDescription(newDescription: String, onError: () -> Unit) {
        val data = HashMap<String, Any>()
        data[DESCRIPTION_KEY] = newDescription

        // Save user description.
        saveUserDataRemote(data, { /* Do nothing */ }, onError)
    }

    override fun saveUserLocation(newLocation: LatLng) =
        geoFirestore.setLocation(currentUserUid(), GeoPoint(newLocation.latitude, newLocation.longitude))

    override fun saveUserOnlineStatus(newIsOnline: Boolean, onComplete: () -> Unit) {
        val data = HashMap<String, Any>()
        data[IS_ONLINE_KEY] = newIsOnline

        saveUserDataRemote(data, onComplete, onComplete)
    }

    override fun deleteUserDataRemote(onSuccess: () -> Unit, onError: () -> Unit) {
        if (isAuthorized) {
            firestore.collection(USERS_COLLECTION).document(currentUserUid())
                .delete()
                .addOnSuccessListener {
                    Timber.tag(TAG).d("User data successfully deleted")
                    onSuccess()

                }
                .addOnFailureListener {
                    Timber.tag(TAG).d("Error deleting user data")
                    onError()
                }

        } else {
            onError()
        }
    }

    override fun startGettingSecondUserUpdates(uid: String) {
        secondUserListenerRegistration = startGettingUserUpdates(uid) { user -> secondUser.value = user }
    }

    override fun stopGettingSecondUserUpdates() = secondUserListenerRegistration?.remove() ?: Unit

    // --- Search ---

    override fun searchResult() = searchResult

    override fun search(queryLatitude: Double, queryLongitude: Double, queryRadius: Double, queryText: String, onComplete: () -> Unit) {
        this.queryText = queryText

        if (isAuthorized
            && queryLatitude != Constants.Map.DEFAULT_LATITUDE
            && queryLongitude != Constants.Map.DEFAULT_LONGITUDE
            && queryRadius != Constants.Map.DEFAULT_RADIUS) {

            clearTempResult()
            stopGettingSearchResultUpdates()

            Timber.tag(TAG).d("Start search: lat = $queryLatitude, lon = $queryLongitude, radius = $queryRadius")

            val queryLocation = GeoPoint(queryLatitude, queryLongitude)

            geoQuery = geoFirestore.queryAtLocation(queryLocation, queryRadius)

            geoQuery?.addGeoQueryDataEventListener(object : GeoQueryDataEventListener {
                override fun onGeoQueryReady() {
                    Timber.tag(TAG).d("onGeoQueryReady")
                    updateSearchResult()
                    onComplete()
                }

                override fun onDocumentExited(doc: DocumentSnapshot?) {
                    Timber.tag(TAG).d("onDocumentExited")
                    Timber.tag(TAG).d(doc.toString())
                    removeUserFromSearchResults(doc?.id)
                }

                override fun onDocumentChanged(doc: DocumentSnapshot?, geoPoint: GeoPoint?) {
                    Timber.tag(TAG).d("onDocumentChanged")
                    Timber.tag(TAG).d(doc.toString())
                    updateUserInSearchResult(doc, geoPoint)
                }

                override fun onDocumentEntered(doc: DocumentSnapshot?, geoPoint: GeoPoint?) {
                    Timber.tag(TAG).d("onDocumentEntered")
                    Timber.tag(TAG).d(doc.toString())
                    updateUserInSearchResult(doc, geoPoint)
                }

                override fun onDocumentMoved(doc: DocumentSnapshot?, geoPoint: GeoPoint?) {
                    Timber.tag(TAG).d("onDocumentMoved")
                    Timber.tag(TAG).d(doc.toString())
                    updateUserInSearchResult(doc, geoPoint)
                }

                override fun onGeoQueryError(exception: Exception?) {
                    Timber.tag(TAG).d(exception)
                    updateSearchResult()
                    onComplete()
                }
            })

        } else {
            onComplete()
        }
    }

    override fun stopGettingSearchResultUpdates() = geoQuery?.removeAllListeners() ?: Unit

    override fun initSearchUserDetails(uid: String) {
        // Get first update of user details from the search results, which are already available
        val user = searchResult.value?.get(uid)
        if (user != null) secondUser.value = user
    }

    override fun initSecondUser(uid: String, name: String, userPicUrl: String) {
        val secondUserValue = createAnonymousUser()
        secondUserValue.uid = uid
        secondUserValue.name = name
        secondUserValue.userPicUrl = userPicUrl
        secondUser.value = secondUserValue
    }

    override fun saveFcmToken(token: String) {
        val data = HashMap<String, Any>()
        data[FCM_TOKEN_KEY] = token

        saveUserDataRemote(data, { /* Do nothing */ }, { /* Do nothing */ })
    }

    // --- Message ---

    override fun messages(): MutableLiveData<MutableList<Message>> = messages

    override fun startGettingMessagesUpdates() {
        // This is needed for the chat room to have the same name,
        // despite of the uid of the user, who started the conversation.
        currentChatRoomUid = if (currentUserUid() < secondUserUid()) "${currentUserUid()}_${secondUserUid()}" else "${secondUserUid()}_${currentUserUid()}"

        if (isAuthorized && currentChatRoomUid != "") {
            // Chatroom collection consists of chatroom documents with chatroom uids.
            // Chatroom uid is calculated as userUid1_userUid2
            // Each chatroom document contains subcollection, which contains chatroom messages.
            // Hierarchy:
            // Chatrooms_collection -> Chatroom_document -> Messages_collection -> Message_document

            messagesListenerRegistration = getMessagesCollectionReference()
                .orderBy(TIMESTAMP_KEY, Query.Direction.DESCENDING)
                .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException == null) {
                        Timber.tag(TAG).d("Messages listen success")

                        val messagesList = mutableListOf<Message>()

                        if (querySnapshot != null) {
                            for (doc in querySnapshot.documents) {
                                val message = getMessageFromDocumentSnapshot(doc)
                                messagesList.add(message)

                                // If message is not from the current user and is not read, then mark it as read
                                // (because the receiver has just read this message).
                                // New message counter of the receiver's chatroom
                                // is decremented by the SERVER (Cloud Functions).
                                if (!message.isFromCurrentUser && !message.isRead) markMessageAsRead(message.uid)
                            }

                        } else {
                            Timber.tag(TAG).d("Messages listen failed")
                        }

                        messages.value = messagesList

                    } else {
                        Timber.tag(TAG).d(firebaseFirestoreException)
                    }
                }
        }
    }

    override fun stopGettingMessagesUpdates() {
        messagesListenerRegistration?.remove()
        currentChatRoomUid = ""
    }

    override fun sendMessage(messageText: String, onError: () -> Unit) {
        if (isAuthorized && currentChatRoomUid != "") {
            val data = HashMap<String, Any>()
            data[SENDER_UID_KEY] = currentUserUid()
            data[RECEIVER_UID_KEY] = secondUserUid()
            data[MESSAGE_TEXT_KEY] = messageText
            data[TIMESTAMP_KEY] = FieldValue.serverTimestamp()  // Get timestamp on the server, not on the device
            data[MESSAGE_IS_READ_KEY] = false

            // Send message to the current chatroom
            getMessagesCollectionReference()
                .add(data)
                .addOnSuccessListener {
                    Timber.tag(TAG).d("Message successfully sent")
                }
                .addOnFailureListener { error ->
                    Timber.tag(TAG).d("Error sending message")
                    onError()
                }

            // Chatrooms for current and second user are updated from the Cloud Functions
            // on every new chat message creation.

        } else {
            onError()
        }
    }

    override fun clearMessages() {
        messages.value = mutableListOf()
    }

    // --- Chatroom ---

    override fun chatrooms() = chatrooms

    override fun startGettingChatroomsUpdates() {
        if (isAuthorized) {
            // We keep a collection of chatrooms for every user.
            // This is needed to easily display a list of all chats,
            // that current user participates in.
            // Chatrooms of users are saved in a separate collection.
            // Hierarchy:
            // userChatrooms_collection -> User_document -> chatroomsOfUser_collection -> Chatroom_document
            // (Note that chatrooms of users are saved NOT in the users collection,
            // but in a separate userChatrooms collection)

            chatroomsUpdateCounter = 0

            chatroomsListenerRegistration = getChatroomsCollectionReference(currentUserUid())
                .orderBy(CHATROOM_LAST_MESSAGE_TIMESTAMP_KEY, Query.Direction.DESCENDING)
                .addSnapshotListener { querySnapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException == null) {
                        Timber.tag(TAG).d("Listen success")

                        val chatroomList = mutableListOf<Chatroom>()
                        var chatroomsContainUnreadMessages = false

                        if (querySnapshot != null) {
                            for (doc in querySnapshot.documents) {
                                val chatroom = getChatroomFromDocumentSnapshot(doc)
                                chatroomList.add(chatroom)
                                if (chatroom.newMessageCount > 0) chatroomsContainUnreadMessages = true
                            }

                        } else {
                            Timber.tag(TAG).d("Listen failed")
                        }

                        // Do not call update unread messages flag
                        // on first time listener is triggered,
                        // because first time is just the first read from Firestore
                        // (nothing has changed yet).
                        if (chatroomsUpdateCounter > 0) setUnreadMessagesExist(chatroomsContainUnreadMessages)

                        chatrooms.value = chatroomList

                        chatroomsUpdateCounter++

                    } else {
                        Timber.tag(TAG).d(firebaseFirestoreException)
                    }
                }
        }
    }

    override fun stopGettingChatroomsUpdates() {
        chatroomsListenerRegistration?.remove() ?: Unit
        chatroomsUpdateCounter = 0
    }

    // === Unread messages ===

    override fun unreadMessagesExist() = unreadMessagesFlag

    override fun setUnreadMessagesExist(value: Boolean) {
        unreadMessagesFlag.value = value
        context.defaultSharedPreferences.edit { putBoolean(UNREAD_MESSAGES_EXIST_KEY, value) }
    }

    // === User pic ===

    override fun changeUserPic(selectedImageUri: Uri, onError: () -> Unit) {
        if (isAuthorized) {
            // This is because resizeImage() must run in background
            GlobalScope.launch {
                // Get reference to current user's pic in Cloud Storage
                // (every user has his own folder with the same name as user's uid).
                // File in the cloud will be recreated on every new upload, so there
                // will be no unused old files.
                val userPicRef = storage.reference.child("${currentUserUid()}/userpic.jpg")

                // Resize selected image
                val byteArray = resizeImage(selectedImageUri)

                // Upload resized image to Cloud Storage
                if (byteArray != null) {
                    userPicRef.putBytes(byteArray)
                        .addOnFailureListener { onError() }
                        .addOnSuccessListener { getDownloadUrlAndUpdateUser(userPicRef, onError) }

                } else {
                    onError()
                }
            }

        } else {
            onError()
        }
    }

    // === Offer ===

    override fun currentUserOfferList() = currentUser.value?.offerList ?: mutableListOf()

    override fun saveOffer(offer: Offer?, onSuccess: () -> Unit, onError: () -> Unit) {
        if (isAuthorized && offer != null) {
            // TODO: implement updating existing offer

            val offerList = currentUserOfferList()
            offerList.add(offer)

            val offerListForSaving = mutableListOf<HashMap<String, Any>>()

            for (offerItem in offerList) {
                val offerForSaving = HashMap<String, Any>()
                offerForSaving[OFFER_TITLE_KEY] = offerItem.title
                offerForSaving[OFFER_DESCRIPTION_KEY] = offerItem.description

                offerListForSaving.add(offerForSaving)
            }

            val data = HashMap<String, Any>()
            data[OFFER_LIST_KEY] = offerListForSaving

            saveUserDataRemote(data, onSuccess, onError)

        } else {
            onError()
        }
    }

    // === Private methods ===
    // --- User ---

    private fun resetCurrentUser() {
        currentUser.value = createAnonymousUser()
    }

    private fun resetSecondUser() {
        secondUser.value = createAnonymousUser()
    }

    private fun createAnonymousUser(): User {
        return User(
            uid = "",
            name = Constants.Auth.DEFAULT_USER_NAME,
            username = "",
            email = Constants.Auth.DEFAULT_USER_MAIL,
            userPicUrl = "",
            description = "",
            isOnline = false,
            location = Constants.Map.DEFAULT_LOCATION
        )
    }

    private fun currentUserUid() = currentUser.value?.uid ?: ""

    private fun secondUserUid() = secondUser.value?.uid ?: ""

    private fun saveUserNameEmailPicAndToken(user: User) {
        // First, get current FCM token
        FirebaseInstanceId.getInstance().instanceId
            .addOnCompleteListener { task ->
                // When getting FCM token task is complete,
                // save it and user's name and email into Firestore
                var token = ""
                if (task.isSuccessful) token = task.result?.token ?: ""

                val data = HashMap<String, Any>()
                data[NAME_KEY] = user.name
                data[EMAIL_KEY] = user.email
                data[FCM_TOKEN_KEY] = token

                // If user from Firebase Auth has user pic
                if (user.userPicUrl != "") {
                    // Load existing user data from Firestore to see if it already has user pic set,
                    // and update it with user pic from Auth if not.
                    loadCurrentUser(
                        // On load success, update data with user pic URL if needed
                        { existingUser -> saveUserDataWithUserPicIfNeeded(data, user.userPicUrl, existingUser) },

                        // On load error, just update name, email and token
                        { saveUserDataWithoutUserPic(data) }
                    )

                } else {
                    saveUserDataWithoutUserPic(data)
                }
            }
    }

    private fun loadCurrentUser(onSuccess: (User) -> Unit, onError: () -> Unit) {
        firestore.collection(USERS_COLLECTION).document(currentUserUid()).get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val document = task.result

                    if (document != null && document.exists()) {
                        onSuccess(getUserFromDocumentSnapshot(document))

                    } else {
                        onError()
                    }

                } else {
                    onError()
                }
            }
    }

    private fun saveUserDataWithUserPicIfNeeded(data: HashMap<String, Any>, userPicUrl: String, existingUser: User) {
        // If existing user data has no user pic,
        // update it with user pic from Firebase Auth
        if (existingUser.userPicUrl == "") data[USER_PIC_URL_KEY] = userPicUrl

        saveUserDataRemote(data, { /* Do nothing */ }, { /* Do nothing */ })
    }

    private fun saveUserDataWithoutUserPic(data: HashMap<String, Any>) =
        saveUserDataRemote(data, { /* Do nothing */ }, { /* Do nothing */ })

    private fun saveUserDataRemote(data: HashMap<String, Any>, onSuccess: () -> Unit, onError: () -> Unit) {
        if (isAuthorized) {
            firestore.collection(USERS_COLLECTION).document(currentUserUid())
                .set(data, SetOptions.merge())  // this is needed to update only the required data if the user exists
                .addOnSuccessListener {
                    Timber.tag(TAG).d("User data successfully written")
                    onSuccess()
                }
                .addOnFailureListener { error ->
                    Timber.tag(TAG).d("Error writing user data")
                    onError()
                }

        } else {
            onError()
        }
    }

    private fun startGettingCurrentUserUpdates() {
        currentUserListenerRegistration = startGettingUserUpdates(currentUserUid()) { user ->
            // If current user has active offer, start sharing location,
            // otherwise stop sharing.
            LocationManager.shareLocation(user.hasActiveOffer())

            currentUser.value = user
        }
    }

    private fun stopGettingCurrentUserUpdates() = currentUserListenerRegistration?.remove()

    private fun startGettingUserUpdates(uid: String, onSuccess: (User) -> Unit): ListenerRegistration? {
        var listenerRegistration: ListenerRegistration? = null

        if (isAuthorized) {
            listenerRegistration = firestore.collection(USERS_COLLECTION).document(uid)
                .addSnapshotListener { snapshot, firebaseFirestoreException ->
                    if (firebaseFirestoreException == null) {
                        if (snapshot != null && snapshot.exists()) {
                            Timber.tag(TAG).d("Listen success")
                            onSuccess(getUserFromDocumentSnapshot(snapshot))

                        } else {
                            Timber.tag(TAG).d("Listen failed")
                        }

                    } else {
                        Timber.tag(TAG).d(firebaseFirestoreException)
                    }
                }
        }

        return listenerRegistration
    }

    private fun getUserFromDocumentSnapshot(doc: DocumentSnapshot) = getUserFromDocumentSnapshot(doc, null)

    private fun getUserFromDocumentSnapshot(doc: DocumentSnapshot, geoPoint: GeoPoint?): User {
        val location = if (geoPoint != null) {
            getUserLocationFromGeoPoint(geoPoint)
        } else {
            getUserLocationFromDocumentSnapshot(doc)
        }

        val user = User(
            uid = doc.id,
            name = doc.getString(NAME_KEY) ?: Constants.Auth.DEFAULT_USER_NAME,
            username = doc.getString(USERNAME_KEY) ?: "",
            email = doc.getString(EMAIL_KEY) ?: Constants.Auth.DEFAULT_USER_MAIL,
            userPicUrl = doc.getString(USER_PIC_URL_KEY) ?: "",
            description = doc.getString(DESCRIPTION_KEY) ?: "",
            isOnline = doc.getBoolean(IS_ONLINE_KEY) ?: false,
            location = location
        )

        user.offerList = getOfferListFromDocumentSnapshot(doc)

        return user
    }

    private fun getUserLocationFromGeoPoint(geoPoint: GeoPoint) = LatLng(geoPoint.latitude, geoPoint.longitude)

    private fun getUserLocationFromDocumentSnapshot(doc: DocumentSnapshot): LatLng {
        val coordinatesList = doc.get(LOCATION_KEY) as List<*>?

        return if (coordinatesList != null && coordinatesList.size == 2) {
            LatLng(coordinatesList[0] as Double, coordinatesList[1] as Double)
        } else {
            Constants.Map.DEFAULT_LOCATION
        }
    }

    private fun getOfferListFromDocumentSnapshot(doc: DocumentSnapshot): MutableList<Offer> {
        val offerSnapshotList = doc.get(OFFER_LIST_KEY) as List<*>?

        val offerList = mutableListOf<Offer>()

        if (offerSnapshotList != null) {
            for (offerSnapshot in offerSnapshotList) {
                val offerMap = offerSnapshot as HashMap<*, *>

                val offerTitle = offerMap[OFFER_TITLE_KEY] as String?
                val offerDescription = offerMap[OFFER_DESCRIPTION_KEY] as String?

                if (offerTitle != null && offerTitle != "" && offerDescription != null && offerDescription != "") {
                    val offer = Offer(offerTitle, offerDescription, 0.0, false, true)
                    offerList.add(offer)
                }
            }
        }

        return offerList
    }

    private fun currentUserNameOrUsername() = currentUser.value?.getUsernameOrName() ?: ""

    private fun currentUserPicUrl() = currentUser.value?.userPicUrl ?: ""

    private fun saveUserPicUrl(newUserPicUrl: String) {
        val data = HashMap<String, Any>()
        data[USER_PIC_URL_KEY] = newUserPicUrl

        saveUserDataRemote(data, { /* Do nothing */ }, { /* Do nothing */ })
    }

    // --- Search ---

    private fun clearResult() {
        clearTempResult()
        updateSearchResult()
    }

    private fun clearTempResult() = tempSearchResult.clear()

    private fun updateUserInSearchResult(doc: DocumentSnapshot?, geoPoint: GeoPoint?) {
        if (doc != null && doc.id != currentUser.value?.uid) {
            val user = getUserFromDocumentSnapshot(doc, geoPoint)

            if (checkConditions(user)) {
                tempSearchResult[user.uid] = user
                updateSearchResult()
            } else {
                removeUserFromSearchResults(user.uid)
            }
        }
    }

    private fun checkConditions(user: User): Boolean = checkQueryText(user)

    // TODO: search in offer titles and descriptions here
    private fun checkQueryText(user: User): Boolean {
        return user.name.contains(queryText, true)
                || user.username.contains(queryText, true)
    }

    private fun removeUserFromSearchResults(uid: String?) {
        if (uid != null) {
            tempSearchResult.remove(uid)
            updateSearchResult()
        }
    }

    private fun updateSearchResult() {
        searchResult.value = tempSearchResult
    }

    // --- Message ---

    private fun getMessageFromDocumentSnapshot(doc: DocumentSnapshot): Message {
        val senderUid = doc.getString(SENDER_UID_KEY) ?: ""

        return Message(
            uid = doc.id,
            timestamp = getTimestampFromDocumentSnapshot(doc, TIMESTAMP_KEY),
            text = doc.getString(MESSAGE_TEXT_KEY) ?: "",
            isFromCurrentUser = senderUid == currentUserUid(),
            isRead = doc.getBoolean(MESSAGE_IS_READ_KEY) ?: false
        )
    }

    private fun getTimestampFromDocumentSnapshot(doc: DocumentSnapshot, timestampKey: String) =
        doc.getTimestamp(timestampKey)?.seconds ?: (System.currentTimeMillis() / 1000)

    private fun getMessagesCollectionReference(): CollectionReference {
        return firestore
            .collection(CHATROOMS_COLLECTION).document(currentChatRoomUid)
            .collection(MESSAGES_COLLECTION)
    }

    private fun markMessageAsRead(messageUid: String) {
        if (isAuthorized && currentChatRoomUid != "") {
            val data = HashMap<String, Any>()
            data[MESSAGE_IS_READ_KEY] = true

            getMessagesCollectionReference()
                .document(messageUid)
                .set(data, SetOptions.merge())
                .addOnSuccessListener {
                    Timber.tag(TAG).d("Message mark as read successfully")
                }
                .addOnFailureListener { error ->
                    Timber.tag(TAG).d("Error mark message as read")
                }
        }
    }

    private fun initUnreadMessagesFlag() {
        unreadMessagesFlag.value = context.defaultSharedPreferences.getBoolean(UNREAD_MESSAGES_EXIST_KEY, false)
    }

    // --- Chatroom ---

    private fun resetChatrooms() {
        chatrooms.value = mutableListOf()
    }

    private fun getChatroomsCollectionReference(userUid: String): CollectionReference {
        return firestore
            .collection(USER_CHATROOMS_COLLECTION).document(userUid)
            .collection(CHATROOMS_OF_USER_COLLECTION)
    }

    private fun getChatroomFromDocumentSnapshot(doc: DocumentSnapshot): Chatroom {
        // Get both user uids and names from document snapshot
        val userUid1 = doc.getString(CHATROOM_USER_UID_1_KEY) ?: ""
        val userUid2 = doc.getString(CHATROOM_USER_UID_2_KEY) ?: ""
        val userName1 = doc.getString(CHATROOM_USER_NAME_1_KEY) ?: ""
        val userName2 = doc.getString(CHATROOM_USER_NAME_2_KEY) ?: ""
        val userPicUrl1 = doc.getString(CHATROOM_USER_PIC_URL_1_KEY) ?: ""
        val userPicUrl2 = doc.getString(CHATROOM_USER_PIC_URL_2_KEY) ?: ""

        // Second user uid is the one, that is not equal to current user uid
        val secondUserUid = if (userUid1 != currentUserUid()) userUid1 else userUid2

        // Second user name is the one, that is not equal to current user name
        val secondUserName = if (userName1 != currentUserNameOrUsername()) userName1 else userName2

        // Second user pic URL is the one, that is not equal to current user pic URL
        val secondUserPicUrl = if (userPicUrl1 != currentUserPicUrl()) userPicUrl1 else userPicUrl2

        return Chatroom(
            secondUserUid = secondUserUid,
            secondUserName = secondUserName,
            secondUserPicUrl = secondUserPicUrl,
            lastMessageText = doc.getString(CHATROOM_LAST_MESSAGE_TEXT_KEY) ?: "",
            lastMessageTimestamp = getTimestampFromDocumentSnapshot(doc, CHATROOM_LAST_MESSAGE_TIMESTAMP_KEY),
            newMessageCount = doc.getLong(CHATROOM_NEW_MESSAGE_COUNT_KEY) ?: 0
        )
    }

    // --- User pic ---

    // Resize selected image to take less space and traffic
    private fun resizeImage(selectedImageUri: Uri): ByteArray? {
        try {
            // Resize image.
            // This must run in background!
            val bitmap = Glide.with(context)
                .asBitmap()
                .load(selectedImageUri)
                .apply(RequestOptions().override(USER_PIC_SIZE).centerCrop().skipMemoryCache(true).diskCacheStrategy(DiskCacheStrategy.NONE))
                .submit().get()

            // Compress into JPEG
            val outStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, outStream)

            return outStream.toByteArray()

        } catch (e: Exception) {
            return null
        }
    }

    private fun getDownloadUrlAndUpdateUser(userPicRef: StorageReference, onError: () -> Unit) {
        // After the image has been uploaded, we can get its download URL
        userPicRef.downloadUrl
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val downloadUri = task.result
                    Timber.tag(TAG).d("Download url = $downloadUri")

                    // Update current user with new user pic download URL
                    saveUserPicUrl(downloadUri.toString())

                } else {
                    onError()
                }
            }
    }
}