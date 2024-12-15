import androidx.compose.foundation.layout.*
import androidx.compose.material.AlertDialog
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadProgressDialog(progress: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Prevent dismissal by default */ },
        title = {
            Text(text = "Downloading Model")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LinearProgressIndicator(
                    progress = progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Downloading... $progress%")
            }
        },
        buttons = {}
    )
}
