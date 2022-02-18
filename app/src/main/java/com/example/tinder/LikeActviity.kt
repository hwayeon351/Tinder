package com.example.tinder

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tinder.DBKey.Companion.DIS_LIKE
import com.example.tinder.DBKey.Companion.LIKE
import com.example.tinder.DBKey.Companion.LIKED_BY
import com.example.tinder.DBKey.Companion.MATCH
import com.example.tinder.DBKey.Companion.NAME
import com.example.tinder.DBKey.Companion.USERS
import com.example.tinder.DBKey.Companion.USER_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.yuyakaido.android.cardstackview.CardStackLayoutManager
import com.yuyakaido.android.cardstackview.CardStackListener
import com.yuyakaido.android.cardstackview.CardStackView
import com.yuyakaido.android.cardstackview.Direction

class LikeActviity: AppCompatActivity(), CardStackListener {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private lateinit var userDB: DatabaseReference

    private val adapter = CardItemAdapter()
    private val cardItems = mutableListOf<CardItem>()

    private val cardStackLayoutManager by lazy {
        CardStackLayoutManager(this, this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_like)

        initCardStackView()
        initSignOutButton()
        initMatchedListButton()

        userDB = Firebase.database.reference.child(USERS)

        val currentUserDB = userDB.child(getCurrentUserId())
        currentUserDB.addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.child(NAME).value == null) {
                    showNameInputPopup()
                    return
                }

                getUnSelectedUsers()

            }

            override fun onCancelled(error: DatabaseError) {

            }

        })

    }

    private fun initCardStackView() {
        val stackView = findViewById<CardStackView>(R.id.cardStackView)
        stackView.layoutManager = cardStackLayoutManager
        stackView.adapter = adapter
    }

    private fun initSignOutButton() {
        val signOutBUtton = findViewById<Button>(R.id.signOutButton)
        signOutBUtton.setOnClickListener {
            auth.signOut()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun initMatchedListButton() {
        val matchedListButton = findViewById<Button>(R.id.matchListButton)
        matchedListButton.setOnClickListener {
            startActivity(Intent(this, MatchedUserActivity::class.java))
        }
    }

    private fun getUnSelectedUsers() {
        userDB.addChildEventListener(object: ChildEventListener{
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (snapshot.child(USER_ID).value != getCurrentUserId()
                    && snapshot.child(LIKED_BY).child(LIKE).hasChild(getCurrentUserId()).not()
                    && snapshot.child(LIKED_BY).child(DIS_LIKE).hasChild(getCurrentUserId()).not()) {

                    val userId = snapshot.child(USER_ID).value.toString()
                    var name = "undecided"
                    if(snapshot.child(NAME).value != null) {
                        name = snapshot.child(NAME).value.toString()
                    }

                    cardItems.add(CardItem(userId, name))
                    adapter.submitList(cardItems)
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                cardItems.find { it.userId == snapshot.key }?.let {
                    it.name = snapshot.child(NAME).value.toString()
                }
                adapter.submitList(cardItems)
                adapter.notifyDataSetChanged()
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {}

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    private fun showNameInputPopup() {
        val editText = EditText(this)

        AlertDialog.Builder(this)
            .setTitle(R.string.write_name)
            .setView(editText)
            .setPositiveButton(R.string.save, { _, _ ->
                if (editText.text.isEmpty()) {
                    showNameInputPopup()
                } else {
                    saveUserName(editText.text.toString())
                }
            })
            .setCancelable(false)
            .show()
    }

    private fun saveUserName(name: String) {

        val userId = getCurrentUserId()
        val currentUserDB = userDB.child(userId)
        val user = mutableMapOf<String, Any>()
        user[USER_ID] = userId
        user[NAME] = name
        currentUserDB.updateChildren(user)
    }


    private fun getCurrentUserId(): String {
        if (auth.currentUser?.uid == null) {
            Toast.makeText(this, R.string.not_login, Toast.LENGTH_SHORT).show()
            finish()
        }

        return auth.currentUser?.uid.orEmpty()
    }

    private fun like() {
        val card = cardItems[cardStackLayoutManager.topPosition - 1]
        cardItems.removeFirst()

        userDB.child(card.userId)
            .child(LIKED_BY)
            .child(LIKE)
            .child(getCurrentUserId())
            .setValue(true)

        saveMatchIfOtherUserLikedMe(card.userId)

        Toast.makeText(this, "${card.name}님을 Like 하셨습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun dislike() {
        val card = cardItems[cardStackLayoutManager.topPosition - 1]
        cardItems.removeFirst()

        userDB.child(card.userId)
            .child(LIKED_BY)
            .child(DIS_LIKE)
            .child(getCurrentUserId())
            .setValue(true)

        Toast.makeText(this, "${card.name}님을 Dislike 하셨습니다.", Toast.LENGTH_SHORT).show()
    }

    private fun saveMatchIfOtherUserLikedMe(otherUserId: String) {
        val otherUserDB = userDB.child(getCurrentUserId()).child(LIKED_BY).child(LIKE).child(otherUserId)
        otherUserDB.addListenerForSingleValueEvent(object: ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true) {
                    userDB.child(getCurrentUserId())
                        .child(LIKED_BY)
                        .child(MATCH)
                        .child(otherUserId)
                        .setValue(true)

                    userDB.child(otherUserId)
                        .child(LIKED_BY)
                        .child(MATCH)
                        .child(getCurrentUserId())
                        .setValue(true)

                }
            }

            override fun onCancelled(error: DatabaseError) {}

        })
    }

    override fun onCardDragging(direction: Direction?, ratio: Float) {}

    override fun onCardSwiped(direction: Direction?) {
        when (direction) {
            Direction.Right -> like()
            Direction.Left -> dislike()
            else -> {

            }
        }
    }

    override fun onCardRewound() {}

    override fun onCardCanceled() {}

    override fun onCardAppeared(view: View?, position: Int) {}

    override fun onCardDisappeared(view: View?, position: Int) {}


}