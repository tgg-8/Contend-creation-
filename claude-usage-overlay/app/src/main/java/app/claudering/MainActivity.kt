package app.claudering

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

/** Einstellungsbildschirm: Werte eintragen, Erlaubnis erteilen, Rädchen starten. */
class MainActivity : Activity() {

    private lateinit var inputUsed: EditText
    private lateinit var inputHours: EditText
    private lateinit var inputMinutes: EditText
    private lateinit var previewLeft: TextView
    private lateinit var permissionCard: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        inputUsed = findViewById(R.id.inputUsed)
        inputHours = findViewById(R.id.inputHours)
        inputMinutes = findViewById(R.id.inputMinutes)
        previewLeft = findViewById(R.id.previewLeft)
        permissionCard = findViewById(R.id.permissionCard)

        inputUsed.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = updatePreview()
        })

        findViewById<Button>(R.id.btnPermission).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            )
        }
        findViewById<Button>(R.id.btnSave).setOnClickListener { saveAndShow() }
        findViewById<Button>(R.id.btnHide).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            Toast.makeText(this, "Rädchen ist ausgeblendet 👋", Toast.LENGTH_SHORT).show()
        }

        prefill()
    }

    override fun onResume() {
        super.onResume()
        permissionCard.visibility =
            if (Settings.canDrawOverlays(this)) View.GONE else View.VISIBLE
    }

    /** Zeigt die zuletzt gespeicherten Werte wieder an. */
    private fun prefill() {
        val usage = UsageStore.load(this) ?: return
        val now = System.currentTimeMillis()
        if (usage.isReset(now)) return
        inputUsed.setText((100 - usage.percentLeft).toString())
        val minutesLeft = (usage.resetAt - now) / 60_000L
        inputHours.setText((minutesLeft / 60).toString())
        inputMinutes.setText((minutesLeft % 60).toString())
    }

    private fun updatePreview() {
        val used = inputUsed.text.toString().toIntOrNull()
        previewLeft.text = if (used != null && used in 0..100) {
            "→ Noch ${100 - used} % übrig"
        } else {
            ""
        }
    }

    private fun saveAndShow() {
        val used = inputUsed.text.toString().toIntOrNull()
        val hours = inputHours.text.toString().ifBlank { "0" }.toIntOrNull()
        val minutes = inputMinutes.text.toString().ifBlank { "0" }.toIntOrNull()

        if (used == null || used !in 0..100) {
            toast("Bitte eine Zahl von 0 bis 100 bei „Genutzt“ eintragen.")
            return
        }
        if (hours == null || minutes == null || hours !in 0..200 || minutes !in 0..59 ||
            hours == 0 && minutes == 0
        ) {
            toast("Bitte eintragen, in wie vielen Stunden und Minuten das Limit zurückgesetzt wird.")
            return
        }

        val resetAt = System.currentTimeMillis() + (hours * 60L + minutes) * 60_000L
        UsageStore.save(this, percentLeft = 100 - used, resetAt = resetAt)

        if (!Settings.canDrawOverlays(this)) {
            toast("Gespeichert! Erteile noch die Erlaubnis oben, dann erscheint das Rädchen.")
            return
        }

        askForNotificationsOnce()
        startForegroundService(
            Intent(this, OverlayService::class.java).setAction(OverlayService.ACTION_REFRESH),
        )
        toast("Gespeichert! Dein Rädchen ist da 🌸")
    }

    /** Ab Android 13 fragt Android, ob die App eine Benachrichtigung zeigen darf. */
    private fun askForNotificationsOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
