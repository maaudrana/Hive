package com.gpetuhov.android.hive.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arellomobile.mvp.presenter.InjectPresenter
import com.gpetuhov.android.hive.R
import com.gpetuhov.android.hive.databinding.FragmentChatBinding
import com.gpetuhov.android.hive.domain.model.Message
import com.gpetuhov.android.hive.domain.model.User
import com.gpetuhov.android.hive.presentation.presenter.ChatFragmentPresenter
import com.gpetuhov.android.hive.presentation.view.ChatFragmentView
import com.gpetuhov.android.hive.ui.adapter.MessagesAdapter
import com.gpetuhov.android.hive.ui.viewmodel.ChatMessagesViewModel
import com.gpetuhov.android.hive.util.moxy.MvpAppCompatFragment
import com.pawegio.kandroid.toast
import kotlinx.android.synthetic.main.fragment_chat.*

class ChatFragment : MvpAppCompatFragment(), ChatFragmentView {

    @InjectPresenter lateinit var presenter: ChatFragmentPresenter

    private var messagesAdapter: MessagesAdapter? = null
    private var binding: FragmentChatBinding? = null
    private var isOpenFromDetails = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = DataBindingUtil.inflate(inflater, R.layout.fragment_chat, container, false)
        binding?.presenter = presenter

        isOpenFromDetails = ChatFragmentArgs.fromBundle(arguments).isOpenFromDetails

        return binding?.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sendButtonEnabled(false)

        val viewModel = ViewModelProviders.of(this).get(ChatMessagesViewModel::class.java)

        messagesAdapter = MessagesAdapter(presenter, viewModel.messages.value)

        messages.layoutManager = LinearLayoutManager(context, RecyclerView.VERTICAL, true)
        messages.adapter = messagesAdapter
        messages.addOnScrollListener(presenter.scrollListener)

        viewModel.messages.observe(this, Observer<MutableList<Message>> { messageList ->
            messagesAdapter?.setMessages(messageList)
        })
        viewModel.secondUser.observe(this, Observer<User> { secondUser ->
            presenter.secondUserUid = secondUser.uid
            binding?.userName = secondUser.getUsernameOrName()
        })
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
    }

    override fun onPause() {
        super.onPause()
        presenter.onPause()
    }

    // === ChatFragmentView

    override fun sendButtonEnabled(isEnabled: Boolean) {
        message_send_button.isEnabled = isEnabled
    }

    override fun showScrollDownButton() {
        if (scroll_down_button.visibility != View.VISIBLE) scroll_down_button.show()
    }

    override fun hideScrollDownButton() {
        if (scroll_down_button.visibility == View.VISIBLE) scroll_down_button.hide()
    }

    override fun clearMessageText() = message_text.setText("")

    override fun openUserDetails() {
        // If chat fragment has been opened from user details fragment,
        // then just pop back stack.
        // Otherwise, open details fragment.
        if (isOpenFromDetails) {
            findNavController().popBackStack()
        } else {
            val action = ChatFragmentDirections.actionChatFragmentToDetailsFragment(true)
            findNavController().navigate(action)
        }
    }

    override fun scrollToLastMessage() = messages.scrollToPosition(0)

    override fun showToast(message: String) {
        toast(message)
    }

    override fun navigateUp() {
        findNavController().navigateUp()
    }
}