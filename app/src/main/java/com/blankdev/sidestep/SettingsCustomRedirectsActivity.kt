package com.blankdev.sidestep

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import androidx.core.content.edit
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.Gravity
import android.widget.Toast
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.color.DynamicColors

class SettingsCustomRedirectsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        DynamicColors.applyToActivitiesIfAvailable(this.application)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_redirects)

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.settingsToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        setupEdgeToEdge()
        setupCustomRedirects()
    }

    private fun setupEdgeToEdge() {
        val root = findViewById<android.view.View>(R.id.settingsRoot)
        val scrollView = findViewById<ScrollView>(R.id.settingsScrollView)
        val toolbar = findViewById<android.view.View>(R.id.settingsToolbar)
        
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            toolbar.setPadding(0, bars.top, 0, 0)
            v.setPadding(bars.left, 0, bars.right, 0)
            scrollView.setPadding(0, 0, 0, bars.bottom)
            insets
        }
    }

    private fun setupCustomRedirects() {
        findViewById<android.view.View>(R.id.btnCustomRedirectsInfo).setOnClickListener {
            showCustomRedirectsInfoModal()
        }
        findViewById<android.view.View>(R.id.btnAddCustomRedirect).setOnClickListener {
            showAddEditRedirectDialog()
        }
        updateCustomRedirectsList()
    }

    private fun updateCustomRedirectsList() {
        val container = findViewById<LinearLayout>(R.id.customRedirectsContainer)
        container.removeAllViews()
        val redirects = getCustomRedirects()

        if (redirects.isEmpty()) {
            val emptyText = TextView(this).apply {
                text = getString(R.string.no_custom_redirects)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
                setTextColor(SettingsUtils.getThemeColor(this@SettingsCustomRedirectsActivity, android.R.attr.textColorSecondary))
                setPadding(0, 16, 0, 16)
                gravity = Gravity.CENTER
            }
            container.addView(emptyText)
            return
        }

        redirects.forEach { redirect ->
            val itemView = layoutInflater.inflate(R.layout.item_custom_redirect, container, false)
            val textDomains = itemView.findViewById<TextView>(R.id.textDomains)
            val textMode = itemView.findViewById<TextView>(R.id.textMode)
            val switchEnabled = itemView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchEnabled)
            val btnDelete = itemView.findViewById<ImageButton>(R.id.btnDelete)

            textDomains.text = "${redirect.originalDomain} → ${redirect.redirectDomain}"
            textMode.text = if (redirect.isAppend) "Append Mode" else "Domain Swap"
            switchEnabled.isChecked = redirect.isEnabled

            // Handle Item Click (Edit)
            itemView.setOnClickListener {
                showAddEditRedirectDialog(redirect)
            }

            // Handle Toggle
            switchEnabled.setOnCheckedChangeListener { _, isChecked ->
                val updated = redirect.copy(isEnabled = isChecked)
                saveCustomRedirect(updated)
            }

            // Handle Delete
            btnDelete.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("Delete redirect")
                    .setMessage("Are you sure you want to delete this redirect?")
                    .setPositiveButton("Delete") { _, _ ->
                        deleteCustomRedirect(redirect.id)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }

            container.addView(itemView)
        }
    }

    private fun showCustomRedirectsInfoModal() {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val padding = SettingsUtils.dp(this@SettingsCustomRedirectsActivity, 24)
            setPadding(padding, 0, padding, 0)
        }

        val info = """
            Configure your own redirection rules for any domain.
            
            • Domain Swap: Replaces the original domain with yours (e.g., example.com/path → mysite.com/path).
            
            • Append Mode: Prepends your redirect URL to the original URL (e.g., archive.ph/https://example.com/path).
            
            Note: Custom redirects prioritize your rules over built-in alternative settings if they overlap.
        """.trimIndent()

        val infoText = TextView(this).apply {
            text = info
            textSize = 16f
            setTextColor(SettingsUtils.getThemeColor(this@SettingsCustomRedirectsActivity, android.R.attr.textColorSecondary))
            setLineSpacing(0f, 1.4f)
            setPadding(0, 0, 0, SettingsUtils.dp(this@SettingsCustomRedirectsActivity, 16))
        }
        dialogView.addView(infoText)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Custom redirects")
            .setView(dialogView)
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun showAddEditRedirectDialog(existing: CustomRedirect? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_redirect, null)
        val inputOriginal = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputOriginalDomain)
        val inputRedirect = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.inputRedirectDomain)
        val toggleMode = dialogView.findViewById<MaterialButtonToggleGroup>(R.id.toggleRedirectType)

        existing?.let {
            inputOriginal.setText(it.originalDomain)
            inputRedirect.setText(it.redirectDomain)
            toggleMode.check(if (it.isAppend) R.id.btnModeAppend else R.id.btnModeSwap)
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(if (existing == null) "Add custom redirect" else "Edit custom redirect")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val original = inputOriginal.text?.toString()?.trim() ?: ""
                val redirect = inputRedirect.text?.toString()?.trim() ?: ""
                val isAppend = toggleMode.checkedButtonId == R.id.btnModeAppend

                if (original.isEmpty() || redirect.isEmpty()) {
                    Toast.makeText(this, "Both domains are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                // Validation for collisions
                if (checkCollision(original, existing?.id)) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Collision detected")
                        .setMessage("A redirect for this domain already exists or conflicts with a built-in rule. Do you want to proceed and override?")
                        .setPositiveButton("Proceed") { _, _ ->
                            saveCustomRedirect(CustomRedirect(existing?.id ?: java.util.UUID.randomUUID().toString(), original, redirect, isAppend, existing?.isEnabled ?: true))
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                } else {
                    saveCustomRedirect(CustomRedirect(existing?.id ?: java.util.UUID.randomUUID().toString(), original, redirect, isAppend, existing?.isEnabled ?: true))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkCollision(domain: String, excludeId: String?): Boolean {
        // Check built-in domains
        val builtIn = listOf("twitter.com", "x.com", "reddit.com", "youtube.com", "youtu.be", "imdb.com", "medium.com", "wikipedia.org", "goodreads.com")
        if (builtIn.any { domain.contains(it, ignoreCase = true) }) return true

        // Check other custom redirects
        val current = getCustomRedirects()
        return current.any { it.id != excludeId && it.originalDomain.equals(domain, ignoreCase = true) }
    }

    private fun getCustomRedirects(): List<CustomRedirect> {
        return SettingsUtils.getCustomRedirects(this)
    }

    private fun saveCustomRedirect(redirect: CustomRedirect) {
        SettingsUtils.saveCustomRedirect(this, redirect)
        updateCustomRedirectsList()
    }

    private fun deleteCustomRedirect(id: String) {
        SettingsUtils.deleteCustomRedirect(this, id)
        updateCustomRedirectsList()
    }

    // saveRedirectsList removed, delegated to SettingsUtils via saveCustomRedirect/deleteCustomRedirect

    // Local helpers removed in favor of SettingsUtils
}
