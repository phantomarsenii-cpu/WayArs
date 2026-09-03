package com.wayars.app.presentation.ui.screen.splash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wayars.app.R
import com.wayars.app.presentation.ui.theme.WaNeonGreen
import com.wayars.app.presentation.ui.theme.WaTextSecondary

@Composable
fun SplashScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "WayArs",
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = WaNeonGreen
        )
        Text(
            text = stringResource(R.string.splash_slogan),
            color = WaTextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(modifier = Modifier.padding(top = 48.dp)) {
            Button(
                onClick = onStart,
                colors = ButtonDefaults.buttonColors(containerColor = WaNeonGreen, contentColor = Color.Black)
            ) {
                Text(stringResource(R.string.splash_start))
            }
        }
    }
}
