package com.trmi.s3monitor.ui.story

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.trmi.s3monitor.R
import com.trmi.s3monitor.databinding.ActivityStoryBinding
import com.trmi.s3monitor.ui.base.BaseScreenActivity

/**
 * Story screen.
 *
 * Per the prompt:
 *  - Top app bar "Story" with arrow_back on the left.
 *    • arrow_back → Home, slide in from the right.
 *  - Outlined card (164dp) with the provided image (123dp wide) filling
 *    the trailing side; "About this Project" headline + "by QuyKhiet DN,
 *    Ami aka. Caude and Ako aka. GPT 🐢" body, both centred.
 *  - Divider (16dp horizontal insets).
 *  - 380x260dp box (surfaceContainerHigh, 28dp corners) with the text
 *    "Đoạn text này có thể sửa trước khi build" at 19sp near the top.
 *  - List item "Web page" with leading open_in_new icon; supporting text
 *    "Ấn để đi tới TRMi "Timeline"".
 *    • Tapping it opens https://laffeyzhieu.github.io/TaskList-Page/ in
 *      a new browser tab (per the prompt's behavior section).
 *  - Secondary nav bar with Story selected.
 *
 * Behavior the sketch leaves out (filled per "General guidance" — "a
 * button or item with no behavior specified should do what its label
 * implies, never nothing"):
 *  - The "Đoạn text này có thể sửa trước khi build" line is presented
 *    as a click-to-edit field. Tapping it opens an input dialog; saving
 *    updates the field for the current session (a Toast notes that the
 *    change resets on rebuild — the persistence layer for the build
 *    preamble is out of scope for this round).
 */
class StoryActivity : BaseScreenActivity<ActivityStoryBinding>() {

    override fun inflateBinding(): ActivityStoryBinding =
        ActivityStoryBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status-bar inset so the toolbar's background extends behind it.
        applyTopBarInsets(binding.topBar)

        // Secondary nav bar: Story is selected.
        setupSecondaryNav(binding.navBarInclude.navBar, R.id.nav_story)

        // arrow_back on the left → Home.
        binding.topBar.setNavigationOnClickListener {
            openMainDestination(R.id.nav_home)
        }

        // Editable text in the box: tap to edit.
        binding.editableText.setOnClickListener { showEditDialog() }

        // "Web page" list item → opens the Timeline page in a browser.
        binding.itemWebPage.setOnClickListener {
            openUrl("https://laffeyzhieu.github.io/TaskList-Page/")
        }
    }

    /**
     * Opens [url] in the user's default browser via ACTION_VIEW. Mirrors
     * the Settings activity's openUrl helper — duplicated deliberately
     * to keep each Activity self-contained rather than threading a
     * shared utility through the constructor.
     */
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    /**
     * Edit dialog for the "Đoạn text này có thể sửa trước khi build"
     * field. Pre-fills with the current text; on save, the in-memory
     * TextView is updated. Persistence across rebuilds is out of scope
     * for this round — the saved Toast is honest about that.
     */
    private fun showEditDialog() {
        val current = binding.editableText.text?.toString().orEmpty()
        val edit = AppCompatEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setText(current)
            setSelection(current.length)
            setSingleLine(false)
            minLines = 3
            // Pad inside the dialog's content area.
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.story_edit_dialog_title)
            .setView(edit)
            .setPositiveButton(R.string.story_edit_dialog_ok) { _, _ ->
                binding.editableText.text = edit.text?.toString().orEmpty()
                Toast.makeText(
                    this,
                    R.string.story_edit_saved_toast,
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setNegativeButton(R.string.story_edit_dialog_cancel, null)
            .show()
    }
}
