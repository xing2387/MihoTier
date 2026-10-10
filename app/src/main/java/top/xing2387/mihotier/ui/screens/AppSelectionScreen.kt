package top.xing2387.mihotier.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun AppSelectionScreen(
    onNavigateToClash: () -> Unit,
    onNavigateToEasyTier: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Mihotier",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Button(
                onClick = onNavigateToClash,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = "Enter Clash Meta",
                    style = MaterialTheme.typography.titleLarge
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onNavigateToEasyTier,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(80.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(
                    text = "Enter EasyTier",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
