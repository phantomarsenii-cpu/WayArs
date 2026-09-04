package com.wayars.app.presentation.ui.screen.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.wayars_logo_full),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        )
        Column(modifier = Modifier.padding(top = 56.dp)) {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
            ) {
                Text(stringResource(R.string.splash_start))
            }
        }
    }
}
