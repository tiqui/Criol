package app.criol

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableIntStateOf
import app.criol.ui.CriolRoot

class MainActivity : ComponentActivity() {
    private val openPlayer = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Graph.player.connect()
        Graph.library.onAppOpened()
        if (savedInstanceState == null && intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            openPlayer.intValue++
        }
        setContent { CriolRoot(openPlayer.intValue) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayer.intValue++
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
