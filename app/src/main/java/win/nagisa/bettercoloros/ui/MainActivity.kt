package win.nagisa.bettercoloros.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import win.nagisa.bettercoloros.ui.components.BetterColorOSApp
import win.nagisa.bettercoloros.ui.theme.BetterColorOSTheme

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * 供 LSPosed Hook 动态重写返回值为 true，用于判断模块激活状态
         */
        @JvmStatic
        fun isModuleActive(): Boolean = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BetterColorOSTheme {
                BetterColorOSApp(isModuleActive = isModuleActive())
            }
        }
    }
}
