package com.trmi.s3monitor.ui.resources

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.trmi.s3monitor.R
import com.trmi.s3monitor.databinding.ActivityResourcesBinding
import com.trmi.s3monitor.ui.base.BaseScreenActivity
class ResourcesActivity : BaseScreenActivity<ActivityResourcesBinding>() {

    // ---- GitHub repos (per spec) ----
    private val repoCodeApp = "https://github.com/LaffeyzHieu/TRMi-App"
    private val repoCodeEsp = "https://github.com/LaffeyzHieu/TRMi-ESP"
    private val repoCodeWeb = "https://github.com/LaffeyzHieu/TaskList-Page"

    // ---- LinkedIn image URL for the Codes card (loaded once Coil/Glide ships) ----
    private val codesImageUrl =
        "https://media.licdn.com/dms/image/v2/D4D12AQH5AQC9TXxYkw/" +
            "article-cover_image-shrink_600_2000/" +
            "article-cover_image-shrink_600_2000/0/1726897313436" +
            "?e=2147483647&v=beta&t=jb4djqtMk72evJrNUfmpML9usqbI_OJUc2Tp3Hgx7Oc"

    // ---- Email recipient (placeholder until the real address is known) ----
    private val mailtoRecipient = "yuhia.rinv@gmail.com"

    // ---- Zalo package name on the Play Store ----
    private val zaloPackage = "com.zing.zalo"

    override fun inflateBinding(): ActivityResourcesBinding =
        ActivityResourcesBinding.inflate(layoutInflater)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Status-bar inset so the toolbar's background extends behind it.
        applyTopBarInsets(binding.topBar)

        // Secondary nav bar: Resources is selected.
        setupSecondaryNav(binding.navBarInclude.navBar, R.id.nav_resources)

        // arrow_back on the left → Home.
        binding.topBar.setNavigationOnClickListener {
            openMainDestination(R.id.nav_home)
        }

        // 3 repo list items → open their GitHub URLs.
        binding.itemCodeApp.setOnClickListener { openUrl(repoCodeApp) }
        binding.itemCodeEsp.setOnClickListener { openUrl(repoCodeEsp) }
        binding.itemCodeWeb.setOnClickListener { openUrl(repoCodeWeb) }

        // Email / Zalo contact items.
        binding.itemEmail.setOnClickListener { openEmail() }
        binding.itemZalo.setOnClickListener { openZalo() }

        // Codes card image: when an image-loading library is added,
        // this is where the LinkedIn URL would be loaded into
        // binding.codesImage. For now the placeholder image icon is
        // already set in the layout XML.
        // loadCodesImage()
    }

    /**
     * Opens [url] in the user's default browser via ACTION_VIEW.
     * Mirrors the openUrl helper in Settings / Story — duplicated
     * deliberately to keep each Activity self-contained.
     */
    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    private fun openEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$mailtoRecipient")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(
                this,
                R.string.resources_no_email_app,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun openZalo() {
        val intent = packageManager.getLaunchIntentForPackage(zaloPackage)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(
                this,
                R.string.resources_zalo_not_installed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
