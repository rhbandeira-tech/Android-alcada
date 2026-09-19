package br.com.ricardobandeira.alcada

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.ricardobandeira.alcada.ui.AlcadaApp
import br.com.ricardobandeira.alcada.ui.theme.AlcadaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { AlcadaTheme { AlcadaApp() } } }
}
