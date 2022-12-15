package org.kaqui.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import org.jetbrains.anko.*
import org.kaqui.BaseActivity
import org.kaqui.R
import org.kaqui.StatsFragment
import org.kaqui.databinding.ItemSelectionActivityBinding
import org.kaqui.model.Classifier
import org.kaqui.model.Database
import org.kaqui.model.LearningDbView
import java.io.Serializable

class ItemSelectionActivity : BaseActivity() {
    private lateinit var dbView: LearningDbView
    private lateinit var listAdapter: ItemSelectionAdapter
    private lateinit var statsFragment: StatsFragment
    private lateinit var mode: SelectionMode
    private var classifier: Classifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mode = intent.getSerializableExtra("mode") as SelectionMode

        if (mode == SelectionMode.KANJI || mode == SelectionMode.WORD) {
            classifier = intent.getParcelableExtra("classifier")
        }

        val binding = ItemSelectionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        statsFragment = StatsFragment.newInstance()
        statsFragment.setShowDisabled(true)
        supportFragmentManager.beginTransaction()
                .replace(R.id.global_stats, statsFragment)
                .commit()

        dbView = when (mode) {
            SelectionMode.HIRAGANA -> Database.getInstance(this).getHiraganaView()
            SelectionMode.KATAKANA -> Database.getInstance(this).getKatakanaView()
            SelectionMode.KANJI -> Database.getInstance(this).getKanjiView(classifier = classifier!!)
            SelectionMode.WORD -> Database.getInstance(this).getWordView(classifier = classifier!!)
        }

        listAdapter = ItemSelectionAdapter(dbView, this, statsFragment)
        binding.itemList.adapter = listAdapter
        binding.itemList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        listAdapter.setup()
    }

    override fun onStart() {
        super.onStart()

        statsFragment.updateStats(dbView)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (mode in arrayOf(SelectionMode.KANJI, SelectionMode.WORD)) {
            menu.add(Menu.NONE, R.id.search, 1, R.string.jlpt_search)
                    .setIcon(android.R.drawable.ic_menu_search)
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        if (mode == SelectionMode.WORD) {
            menu.add(Menu.NONE, R.id.autoselect, 2, R.string.autoselect_from_kanji)
        }

        if (mode == SelectionMode.HIRAGANA || mode == SelectionMode.KATAKANA) {
            menu.add(Menu.NONE, R.id.save_selection, 3, "Save selection")
            menu.add(Menu.NONE, R.id.load_selection, 4, "Load selection")
        }

        menu.add(Menu.NONE, R.id.select_all, 5, R.string.select_all)
        menu.add(Menu.NONE, R.id.select_none, 6, R.string.select_none)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.search -> {
                startActivity<ItemSearchActivity>(
                        "mode" to (when (mode) {
                            SelectionMode.KANJI -> ItemSearchActivity.Mode.KANJI
                            SelectionMode.WORD -> ItemSearchActivity.Mode.WORD
                            else -> throw RuntimeException("Can't search on this item type!")
                        }))
                return true
            }
            R.id.autoselect -> {
                alert {
                    titleResource = R.string.override_selection_title
                    messageResource = R.string.override_selection_msg
                    positiveButton(android.R.string.yes) {
                        Database.getInstance(this@ItemSelectionActivity).autoSelectWords(classifier!!)
                        listAdapter.notifyDataSetChanged()
                        statsFragment.updateStats(dbView)
                    }
                    negativeButton(android.R.string.no) {}
                }.show()
                return true
            }
            R.id.select_all -> {
                dbView.setAllEnabled(true)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats(dbView)
                return true
            }
            R.id.select_none -> {
                dbView.setAllEnabled(false)
                listAdapter.notifyDataSetChanged()
                statsFragment.updateStats(dbView)
                return true
            }
            R.id.save_selection -> {
                alert {
                    title = getString(R.string.enter_name_of_selection)
                    var name: EditText? = null
                    customView = UI {
                        linearLayout {
                            name = editText {
                                inputType = InputType.TYPE_CLASS_TEXT
                            }.lparams(width = matchParent, height = wrapContent) {
                                horizontalMargin = dip(16)
                            }
                        }
                    }.view
                    positiveButton(android.R.string.ok) { saveSelection(name!!.text.toString()) }
                    negativeButton(android.R.string.cancel) {}
                }.show()
                return true
            }
            R.id.load_selection -> {
                // TODO: onActivityResult is deprecated, use the new android methods instead.
                // TODO: Also change requestCode to const.
                startActivityForResult<SavedSelectionsActivity>(111,"mode" to mode as Serializable)
                return true
            }
            else ->
                return super.onOptionsItemSelected(item)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if ( requestCode == 111 ) {
            listAdapter.notifyDataSetChanged()
        }
    }

    private fun saveSelection(name: String) {
        when (mode) {
            SelectionMode.KATAKANA -> Database.getInstance(this).saveKanaSelectionTo(Database.KANAS_TYPE_KATAKANA, name)
            SelectionMode.HIRAGANA -> Database.getInstance(this).saveKanaSelectionTo(Database.KANAS_TYPE_HIRAGANA, name)
            else -> throw RuntimeException("Can't save selection for this type from this class!")
        }
        toast(getString(R.string.saved_selection, name))
    }

    companion object {
        private const val TAG = "ItemSelectionActivity"
    }
}
