package com.example.rpodmp_lab_1

import android.app.SearchManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.SearchView
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.ResultCallback
import com.google.android.gms.drive.Drive
import com.google.android.gms.drive.DriveApi
import com.google.android.gms.drive.DriveContents
import com.google.android.gms.drive.MetadataChangeSet
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.row.view.*
import java.io.OutputStreamWriter

class MainActivity : AppCompatActivity(),
    GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    var listNotes = ArrayList<Note>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        //Load from DB
        LoadQuery("%")
    }

    override fun onResume() {
        super.onResume()
        LoadQuery("%")
    }

    private fun LoadQuery(title: String) {
        var dbManager = DbManager(this)
        val projections = arrayOf("ID", "Title", "Description")
        val selectionArgs = arrayOf(title)
        val cursor = dbManager.Query(projections, "Title like ?", selectionArgs, "Title")
        listNotes.clear()
        if (cursor.moveToFirst()) {
            do {
                val ID = cursor.getInt(cursor.getColumnIndex("ID"))
                val Title = cursor.getString(cursor.getColumnIndex("Title"))
                val Description = cursor.getString(cursor.getColumnIndex("Description"))
                listNotes.add(Note(ID, Title, Description))
            } while (cursor.moveToNext())
        }
//adapter
        var myNotesAdapter = MyNotesAdapter(this, listNotes) //set adapter
        notesLv.adapter = myNotesAdapter
        //get total number of tasks from ListView
        val total = notesLv.count //actionbar
        val mActionBar = supportActionBar
        if (mActionBar != null) {
            //set to actionbar as subtitle of actionbar
            mActionBar.subtitle = "$total note(s)"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        //searchView
        val sv: SearchView? = menu!!.findItem(R.id.app_bar_search).actionView as? SearchView
        val sm = getSystemService(Context.SEARCH_SERVICE) as SearchManager

        if(sv == null){
            return super.onCreateOptionsMenu(menu)
        }

        sv.setSearchableInfo(sm.getSearchableInfo(componentName))
        sv.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                LoadQuery("%" + query + "%")
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                LoadQuery("%" + newText + "%")
                return false
            }
        })

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                R.id.addNote -> {
                    startActivity(Intent(this, AddNoteActivity::class.java))
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    inner class MyNotesAdapter : BaseAdapter {
        var listNotesAdapter = ArrayList<Note>()
        var context: Context? = null

        constructor(context: Context, listNotesAdapter: ArrayList<Note>) : super() {
            this.listNotesAdapter = listNotesAdapter
            this.context = context
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            //inflate layout row.xml
            var myView = layoutInflater.inflate(R.layout.row, null)
            val myNote = listNotesAdapter[position]
            myView.titleTv.text = myNote.nodeName
            myView.descTv.text = myNote.nodeDes

            //delete button click
            myView.deleteBtn.setOnClickListener {
                var dbManager = DbManager(this.context!!)
                val selectionArgs = arrayOf(myNote.nodeID.toString())
                dbManager.delete("ID=?", selectionArgs)
                LoadQuery("%")
            }

            //edit//update button click
            myView.setOnClickListener {
                GoToUpdateFun(myNote)
            } //copy btn click

            myView.copyBtn.setOnClickListener {
                //get title
                val title = myView.titleTv.text.toString() //get description
                val desc = myView.descTv.text.toString() //concatinate
                val s = title + "\n" + desc

                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.text = s // add to clipboard
                Toast.makeText(this@MainActivity, "Copied...", Toast.LENGTH_SHORT).show()
            }
            //share btn click
            myView.shareBtn.setOnClickListener {
                val title = myView.titleTv.text.toString()
                val desc = myView.descTv.text.toString()
                val s = title + "\n" + desc
                val shareIntent = Intent()
                shareIntent.action = Intent.ACTION_SEND
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, s)
                startActivity(Intent.createChooser(shareIntent, title))
            }

            return myView
        }

        override fun getItem(position: Int): Any {
            return listNotesAdapter[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getCount(): Int {
            return listNotesAdapter.size
        }

    }

    private fun GoToUpdateFun(myNote: Note) {
        var intent = Intent(this, AddNoteActivity::class.java)
        intent.putExtra(
            "ID",
            myNote.nodeID
        )
        intent.putExtra("name", myNote.nodeName) //put title
        intent.putExtra("des", myNote.nodeDes) //put description
        startActivity(intent) //start activity
    }

    override fun onConnected(bundle: Bundle?) {
        Toast.makeText(this,"DriveConnected", Toast.LENGTH_LONG).show()
    }

    override fun onConnectionSuspended(bundle: Int) {
        Log.i("DriveSuspended", "GoogleApiClient connection suspended")
    }

    override fun onConnectionFailed(result: ConnectionResult) {
        Log.i("DriveFailed", "GoogleApiClient connection failed: " + result.toString())

        if (!result.hasResolution()) {

            // show the localized error dialog.
            GoogleApiAvailability.getInstance().getErrorDialog(this, result.getErrorCode(), 0).show()
            return
        }

        /**
         *  The failure has a resolution. Resolve it.
         *  Called typically when the app is not yet authorized, and an  authorization
         *  dialog is displayed to the user.
         */

        try {
            result.startResolutionForResult(this, 1000)
        } catch (e: IntentSender.SendIntentException) {
            Log.e("DriveSendException", "Exception while starting resolution activity&quot")
        }
    }

}