package win.nagisa.oppocameranormalization.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import win.nagisa.oppocameranormalization.R
import win.nagisa.oppocameranormalization.util.FilenameNormalizer

class MainActivity : AppCompatActivity() {

    companion object {
        /**
         * 供 Xposed Hook 替换返回值为 true，用于判断模块是否在 LSPosed 中激活
         */
        @JvmStatic
        fun isModuleActive(): Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupStatusCard()
        setupTester()
    }

    private fun setupStatusCard() {
        val cardStatus = findViewById<MaterialCardView>(R.id.cardStatus)
        val tvStatusTitle = findViewById<TextView>(R.id.tvStatusTitle)

        if (isModuleActive()) {
            tvStatusTitle.text = getString(R.string.module_status_active)
            tvStatusTitle.setTextColor(Color.parseColor("#008844"))
            cardStatus.strokeColor = Color.parseColor("#00AA55")
        } else {
            tvStatusTitle.text = getString(R.string.module_status_inactive)
            tvStatusTitle.setTextColor(Color.parseColor("#D32F2F"))
            cardStatus.strokeColor = Color.parseColor("#EF5350")
        }
    }

    private fun setupTester() {
        val etTestInput = findViewById<TextInputEditText>(R.id.etTestInput)
        val tvTestOutput = findViewById<TextView>(R.id.tvTestOutput)

        etTestInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s?.toString() ?: ""
                val output = FilenameNormalizer.normalize(input)
                tvTestOutput.text = output
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }
}
