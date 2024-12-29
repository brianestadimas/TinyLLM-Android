package org.saltedfish.chatbot

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.core.content.ContextCompat.startActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

data class Photo(
    var id :Int=0,
    val uri: Uri,
    val request: ImageRequest?
)
val PROMPT = """<|im_start|>system
You are an expert in composing function.<|im_end|>
<|im_start|>user

Here is a list of functions:

%DOC%

Now my query is: %QUERY%
<|im_end|>
<|im_start|>assistant
"""
val MODEL_NAMES = arrayOf("PhoneLM","Qwen1.5","SmoLLM", "Phi3V", "Phi3V-Design")
val vision_model = "phi3v_q4_k.mllm"
val vision_model_finetuned = "phi3v_q4_k_finetuned.mllm"
val vision_vocab = "model/phi3v_vocab.mllm"
class ChatViewModel : ViewModel() {
//    private var _inputText: MutableLiveData<String> = MutableLiveData<String>()
//    val inputText: LiveData<String> = _inputText
    private var _messageList: MutableLiveData<List<Message>> = MutableLiveData<List<Message>>(
    listOf()
)
    private var _photoList: MutableLiveData<List<Photo>> = MutableLiveData<List<Photo>>(
        listOf()
    )
    var functions_:Functions? = null
    var docVecDB:DocumentVecDB? = null
    val photoList = _photoList
    private var _previewUri: MutableLiveData<Uri?> = MutableLiveData<Uri?>(null)
    val previewUri = _previewUri
    var _scrollstate:ScrollState? = null
    private var _lastId = 0;
    val messageList= _messageList
    var _isExternalStorageManager = MutableLiveData<Boolean>(false)
    var _isBusy = MutableLiveData<Boolean>(true)
    val isBusy = _isBusy
    val _isLoading = MutableLiveData<Boolean>(true)
    val isLoading = _isLoading
    private var _modelType = MutableLiveData<Int>(0)
    private var _modelId = MutableLiveData<Int>(0)
    private var _visionId = MutableLiveData<Int>(0)
    val visionId = _visionId
    val modelId = _modelId
    val modelType = _modelType
    private var profiling_time = MutableLiveData<DoubleArray>()
    val profilingTime = profiling_time

    private val _downloadProgress = MutableLiveData<Int>()
    val downloadProgress: LiveData<Int> = _downloadProgress

    private val _isDownloading = MutableLiveData<Boolean>(false)
    val isDownloading: LiveData<Boolean> = _isDownloading

    private val _downloadCompleted = MutableLiveData<Boolean>()
    val downloadCompleted: LiveData<Boolean> = _downloadCompleted

    private val _downloadError = MutableLiveData<String?>()
    val downloadError: LiveData<String?> = _downloadError

    // Downloader instance
    private val downloader = Downloader()

    private var _backendType = -1
    fun setModelType(type:Int){
        _modelType.value = type
    }
    fun setBackendType(type:Int){
        _backendType=type
    }
    fun setModelId(id:Int){
        _modelId.value = id
    }
    fun setVisionId(id:Int){
        _visionId.value = id
    }
    fun setPreviewUri(uri: Uri?){
        _previewUri.value = uri
    }
    fun addPhoto(photo: Photo):Int{
        photo.id = _photoList.value?.size?:0
        val list = (_photoList.value?: listOf()).plus(photo)
        _photoList.postValue(list)
        return photo.id
    }

    init {

    JNIBridge.setCallback { id,value, isStream,profile ->
            Log.i("chatViewModel","id:$id,value:$value,isStream:$isStream profile:${profile.joinToString(",")}")
            updateMessage(id,value.trim().replace("|NEWLINE|","\n").replace("▁"," "),isStream)
            if (!isStream){
                _isBusy.postValue(false)
               if(profile.isNotEmpty()) profiling_time.postValue(profile)
            }
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        _isExternalStorageManager.value = Environment.isExternalStorageManager()
        } else {
            TODO("VERSION.SDK_INT < R")
        }


    }
    fun addMessage(message: Message,remote:Boolean=false) {
        if (message.isUser){
                message.id = _lastId++
            }
        val list = (_messageList.value?: listOf()).plus(message)

        if (remote){
            _messageList.postValue(list)
        }
        else{
            _messageList.value = list

        }
    }
    fun sendInstruct(content: Context,message: Message){
        if (message.isUser){
            addMessage(message)
            val bot_message = Message("...",false,0)
            bot_message.id = _lastId++
            addMessage(bot_message)
            _isBusy.value = true
            CoroutineScope(Dispatchers.IO).launch {
                val query = docVecDB?.queryDocument(message.text)
                Log.i("chatViewModel","query:$query")
                val query_docs = query?.map { it.generateAPIDoc() }?.joinToString("==================================================\n")
                val prompt = PROMPT.replace("%QUERY%",message.text).replace("%DOC%",query_docs?:"")
                Log.i("prompt", prompt)
                val len = prompt.length
                Log.i("prompt Len  ","$len")
                JNIBridge.run(bot_message.id,prompt,100,false)
            }
        }
    }
    fun sendMessage(context: Context, message: Message) {
        if (modelType.value == 4) {
            sendInstruct(context, message)
            return
        }
        if (message.isUser) {
            addMessage(message)
            val bot_message = Message("...", false, 0)
            bot_message.id = _lastId++
            addMessage(bot_message)
            _isBusy.value = true
            if (arrayOf(0, 2, 3).contains(modelType.value)) {
                viewModelScope.launch(Dispatchers.IO) {
                    val profiling_time = JNIBridge.run(bot_message.id, message.text, 100)
                    Log.i("chatViewModel", "profiling_time:$profiling_time")
                }
            } else if (modelType.value == 1) {
                val imagePath = if (message.type == MessageType.IMAGE) {
                    val uri = message.content as Uri?
                    uri?.let { getImagePathFromUri(context, it) } ?: ""
                } else {
                    ""
                }

                if (imagePath.isNotEmpty()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        JNIBridge.runImage(bot_message.id, imagePath, message.text, 100)
                    }
                } else {
                    // Handle the case where imagePath is not available
                    Log.e("ChatViewModel", "Image path is empty or invalid.")
                }
            }
        }
    }


    fun initStatus(context: Context, modelType: Int = _modelType.value ?: 0) {
        viewModelScope.launch(Dispatchers.Main) {
            val vision_id = visionId.value ?: 1
            val model_id = when (modelType) {
                1 -> {
                    when (vision_id) {
                        1 -> 3
                        2 -> 4
                        else -> 3
                    }
                }
                else -> modelId.value ?: 0
            }
            Log.i("Vision", "vision_id:$vision_id")
            Log.i("ChatViewModel", "model_id:$model_id")
            val modelPath = when (modelType) {
                1 -> {
                    when (vision_id) {
                        1 -> vision_model
                        2 -> vision_model_finetuned
                        else -> vision_model
                    }
                }
                3 -> {
                    when (model_id) {
                        0 -> "phonelm-1.5b-instruct-q4_0_4_4.mllm"
                        1 -> "qwen-1.5-1.8b-chat-q4_0_4_4.mllm"
                        2 -> "smollm-1.7b-instruct-q4_0_4_4.mllm"
                        else -> "phonelm-1.5b-instruct-q4_0_4_4.mllm"
                    }
                }

                else -> "phonelm-1.5b-instruct-q4_0_4_4.mllm"
            }

            val modelUrl = when (modelType) {
                3 -> {
                    when (model_id) {
                        0 -> "https://huggingface.co/mllmTeam/phonelm-1.5b-mllm/resolve/main/phonelm-1.5b-instruct-q4_0_4_4.mllm"
                        1 -> "https://huggingface.co/mllmTeam/qwen-1.5-1.8b-chat-mllm/resolve/main/qwen-1.5-1.8b-chat-q4_0_4_4.mllm"
                        2 -> "https://huggingface.co/mllmTeam/smollm-1.7b-instruct-mllm/resolve/main/smollm-1.7b-instruct-q4_0_4_4.mllm"
                        else -> "https://huggingface.co/mllmTeam/phonelm-1.5b-mllm/resolve/main/phonelm-1.5b-instruct-q4_0_4_4.mllm"
                    }
                }
                1 -> {
                    when (vision_id) {
                        1 -> "https://huggingface.co/brianestadimas/Phi-3-Vision-Q4-MLLM/resolve/main/phi3v_q4_k.mllm"
                        2 -> "https://huggingface.co/brianestadimas/Phi-3-Vision-Q4-Finetuned-MLLM/resolve/main/phi3v_q4_k_finetuned.mllm"
                        else -> "https://huggingface.co/brianestadimas/Phi-3-Vision-Q4-MLLM/resolve/main/phi3v_q4_k.mllm"
                    }
                }
                else -> "https://huggingface.co/mllmTeam/phonelm-1.5b-mllm/resolve/main/phonelm-1.5b-instruct-q4_0_4_4.mllm"
            }

            var vacabPath = when (modelType) {
                1 -> vision_vocab
                3 -> {
                    when (model_id) {
                        0 -> "model/phonelm_vocab.mllm"
                        1 -> "model/qwen_vocab.mllm"
                        2 -> "model/smollm_vocab.mllm"
                        else -> ""
                    }
                }

                else -> ""
            }

            var mergePath = when (model_id) {
                0 -> "model/phonelm_merges.txt"
                1 -> "model/qwen_merges.txt"
                2 -> "model/smollm_merges.txt"
                else -> ""
            }

            var downloadsPath = getDownloadsPath(context)
            val modelFile = File(downloadsPath, modelPath)

            val assetsCopied = withContext(Dispatchers.IO) { copyAssetsIfNotExist(context) }
            if (!assetsCopied) {
                handleUIError(context, "Failed to copy assets.")
                return@launch
            }

            if (!modelFile.exists()) {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.custom_progress_toast, null)
                val toast = Toast(context.applicationContext).apply {
                    duration = Toast.LENGTH_LONG
                    view = layout
                }
                toast.show()
                val downloadComplete = CompletableDeferred<Boolean>()

                // Track the last Toast percentage to ensure we only show updates every 5%
                var lastToastProgress = 0

                try {
                    // Start the download
                    downloader.downloadFile(
                        url = modelUrl,
                        directory = downloadsPath,
                        fileName = modelPath
                    ) { progress: Int ->
                        _downloadProgress.postValue(progress) // Update progress LiveData

                        // Show Toast only if progress has increased by 5% or more
                        if (progress - lastToastProgress >= 5) {
                            lastToastProgress = progress
                            Handler(Looper.getMainLooper()).post {
                                Toast.makeText(
                                    context,
                                    "Downloading ${MODEL_NAMES[model_id]}... $progress%",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }

                    // Mark the download as complete
                    downloadComplete.complete(true)
                } catch (e: IOException) {
                    Log.e("Download", "Failed to download model: ${e.message}")
                    handleDownloadError(context, "Failed to download model. Please try again.")
                    downloadComplete.completeExceptionally(e)
                }
            }
            val load_model = when (modelType) {
                1 -> 1
                3, 4 -> {
                    when (model_id) {
                        0 -> 3
                        1 -> 4
                        2 -> 5
                        else -> 0
                    }
                }
                else -> 0
            }

            try {
                val result = JNIBridge.Init(
                    load_model,
                    downloadsPath,
                    modelPath,
                    qnnmodelPath = "",
                    vacabPath,
                    mergePath,
                    _backendType
                )

                if (result) {
                    addMessage(Message("Model ${MODEL_NAMES[model_id]} Loaded!", false, 0), true)
                    _isLoading.postValue(false)
                    _isBusy.postValue(false)
                    _downloadCompleted.postValue(true)
                } else {
                    handleDownloadError(context, "Fail To Load Models! Please check files.")
                }

            } catch (e: RuntimeException) {
                FirebaseCrashlytics.getInstance().recordException(e)
//                handleDownloadError(context, "Error initializing model: ${e.message}")
            }
        }
    }
    private fun handleDownloadError(context: Context, errorMessage: String) {
        _downloadError.postValue(errorMessage)
        _isLoading.postValue(false)
        _isBusy.postValue(false)

        // Show error Toast on the main thread
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }
    }

    fun handleUIError(context: Context, errorMessage: String) {
        // Log the error for debugging
        Log.e("ChatViewModel", errorMessage)

        // Add the error to the UI
        viewModelScope.launch(Dispatchers.Main) {
            addMessage(Message(errorMessage, false, 0), true)
        }

        // Show the error as a Toast for immediate feedback
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, errorMessage, Toast.LENGTH_LONG).show()
        }
    }



    fun handleError(context: Context, errorMessage: String) {
        // Log the error
        Log.e("ChatViewModel", errorMessage)

        // Show error as a Toast (runs on main thread)
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
        }

        // Optionally write the error to a file for debugging
        try {
            val errorLogFile = File(context.filesDir, "error_log.txt")
            errorLogFile.appendText("$errorMessage\n")
        } catch (fileException: Exception) {
            Log.e("ChatViewModel", "Failed to write error to file: ${fileException.message}")
        }
    }

    fun updateMessage(id:Int,content:String,isStreaming:Boolean=true){
        val index = _messageList.value?.indexOfFirst { it.id == id }?:-1
        if (index == -1) {
            Log.i("chatViewModel","updateMessage: index == -1")
            return
        }
        val message = _messageList.value?.get(index)?.copy()

        if (message!=null){
            message.text = content
            message.isStreaming= isStreaming
            val list = (_messageList.value?: mutableListOf()).toMutableList()
            // change the item of immutable list
            list[index] = message
            _messageList.postValue(list.toList())
        }
        if (!isStreaming&&modelType.value==4){
            message?.text="Done for you."
           val functions = parseFunctionCall(content)
            functions.forEach {
                functions_?.execute(it)
            }
        }
    }
}
class VQAViewModel : ViewModel() {
    val messages = listOf(
        "What's the message conveyed by screen?",
        "When is the meal reservation?",
        "Summarize The Screenshot."
    )
    lateinit var bitmap: Bitmap
    private var _selectedMessage: MutableLiveData<Int> = MutableLiveData<Int>(-1)
    val selectedMessage = _selectedMessage
    private var _answerText: MutableLiveData<String?> = MutableLiveData<String?>(null)
    val answerText = _answerText
    var result_: Boolean = false

    fun setSelectedMessage(id: Int) {
        _selectedMessage.value = id
        if (result_ && id > -1) {
            sendMessage(messages[id], context = null) // Context will be passed later
        }
    }

    init {
        JNIBridge.setCallback { id, value, isStream, profile ->
            Log.i("VQAViewModel", "id:$id,value:$value,isStream:$isStream")
            _answerText.postValue(value.trim().replace("|NEWLINE|", "\n").replace("▁", " "))
        }
    }

    /**
     * Initialize the status by loading the model and preparing the bitmap.
     */
    fun initStatus(context: Context) {
        if (result_ || answerText.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            bitmap = BitmapFactory.decodeResource(context.resources, R.drawable.chat_record_demo)
            bitmap = Bitmap.createScaledBitmap(bitmap, 210, 453, true)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val result = JNIBridge.Init(1, getDownloadsPath(context), "phi3v.mllm", "", "vocab_uni.mllm")
            result_ = result
            if (result && selectedMessage.value != null && selectedMessage.value!! > -1) {
                sendMessage(messages[selectedMessage.value!!], context)
            } else if (!result) {
                _answerText.postValue("Fail to Load Models.")
            }
        }
    }

    /**
     * Sends a message with the exact image path.
     * @param message The message text to send.
     * @param context The context required to access file system.
     */
    fun sendMessage(message: String, context: Context?) {
        if (context == null) {
            Log.e("VQAViewModel", "Context is null. Cannot send message.")
            return
        }

        // Save the bitmap to a file to obtain the image path
        val imagePath = saveBitmapToFile(context, bitmap, "vqa_image.png")
        if (imagePath != null) {
            viewModelScope.launch(Dispatchers.IO) {
                JNIBridge.runImage(0, imagePath, message, 100)
            }
        } else {
            Log.e("VQAViewModel", "Failed to save bitmap to file.")
        }
    }
}

class SummaryViewModel:ViewModel(){
    private var _message: MutableLiveData<Message> = MutableLiveData<Message>()
    val message = _message
    private var _result: MutableLiveData<Boolean> = MutableLiveData<Boolean>(false)
    val result = _result



    private fun updateMessageText(message:String){
        val msg = _message.value?.copy()?: Message("...",false,0)
        msg.text = message
        _message.postValue(msg)
    }

    init {
        JNIBridge.setCallback { id,value, isStream ,profile->
            Log.i("SummaryViewModel","id:$id,value:$value,isStream:$isStream")
            updateMessageText(value.trim().replace("|NEWLINE|","\n").replace("▁"," "))
        }
    }
    fun initStatus(context: Context){

        viewModelScope.launch(Dispatchers.IO) {
            val result =JNIBridge.Init(1,getDownloadsPath(context),"smollm.mllm","", "vocab_smollm.mllm")
            _result.postValue(result)
            if (!result){
                updateMessageText("Fail to Load Models.")
            }
        }
    }
    fun sendMessage(message: String){
        val msg = Message("...", false, 0)
        _message.postValue(msg)
        viewModelScope.launch(Dispatchers.IO)  {
            JNIBridge.run(msg.id,message,100)
        }
}}
class PhotoViewModel : ViewModel() {
    private var _message: MutableLiveData<Message> = MutableLiveData<Message>()
    val message = _message
    private var _bitmap = MutableLiveData<Bitmap>()
    var result_: Boolean = false

    /**
     * Updates the message text in the LiveData.
     */
    private fun updateMessageText(message: String) {
        val msg = _message.value?.copy() ?: Message("...", false, 0)
        msg.text = message
        _message.postValue(msg)
    }

    /**
     * Sets the bitmap after resizing it to 224x224.
     * If the model is initialized and there's no existing message, it sends a description request.
     */
    fun setBitmap(bitmap: Bitmap, context: Context) {
        // Resize bitmap to 224x224
        val width = bitmap.width
        val height = bitmap.height
        val newWidth = 224
        val newHeight = 224
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        val matrix = android.graphics.Matrix()
        matrix.postScale(scaleWidth, scaleHeight)
        val resizedBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
        _bitmap.value = resizedBitmap
        Log.e("PhotoViewModel", "bitmap:${resizedBitmap.width},${resizedBitmap.height}")

        if (result_ && _message.value == null) {
            sendMessage("Describe this photo.", context)
        }
    }

    init {
        JNIBridge.setCallback { id, value, isStream, profile ->
            Log.i("PhotoViewModel", "id:$id,value:$value,isStream:$isStream")
            updateMessageText(value.trim().replace("|NEWLINE|", "\n").replace("▁", " "))
        }
        // Initialize the model status externally after setting the bitmap
    }

    /**
     * Initializes the model status by loading the necessary models.
     * This should be called after setting the bitmap.
     */
    fun initStatus(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = JNIBridge.Init(
                1,
                getDownloadsPath(context),
                "phi3v.mllm",
                "",
                "vocab_uni.mllm"
            )
            result_ = result
            if (result && _message.value == null && _bitmap.value != null) {
                sendMessage("Describe this photo.", context)
            } else if (!result) {
                updateMessageText("Fail to Load Models.")
            }
        }
    }

    /**
     * Sends a message with the exact image path.
     * @param message The message text to send.
     * @param context The context required to access the file system.
     */
    fun sendMessage(message: String, context: Context) {
        // Save the bitmap to a file to obtain the image path
        val imagePath = saveBitmapToFile(context, _bitmap.value, "photo_view_model_image.png")
        if (imagePath != null) {
            val msg = Message("...", false, 0)
            _message.postValue(msg)
            viewModelScope.launch(Dispatchers.IO) {
                JNIBridge.runImage(msg.id, imagePath, message, 100)
            }
        } else {
            Log.e("PhotoViewModel", "Failed to save bitmap to file.")
            updateMessageText("Failed to process the image.")
        }
    }
}

private fun bitmap2Bytes(bitmap: Bitmap): ByteArray {
    val byteArrayOutputStream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 80, byteArrayOutputStream)
    return byteArrayOutputStream.toByteArray()
}

fun getImagePathFromUri(context: Context, uri: Uri): String? {
    var path: String? = null
    // Check if the Uri scheme is "content"
    if (uri.scheme == "content") {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = cursor.getString(columnIndex)
            }
        }
    } else if (uri.scheme == "file") {
        // Handle the case where the Uri is a direct file path
        path = uri.path
    }

    // If no path is found, return null or handle gracefully
    return path
}


/**
 * Copies the content from a Uri to a file in the cache directory and returns the file path.
 */
//fun copyUriToFile(context: Context, uri: Uri): String? {
//    return try {
//        val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
//        val file = File(context.cacheDir, "image_${System.currentTimeMillis()}.png")
//        val outputStream = FileOutputStream(file)
//        inputStream?.copyTo(outputStream)
//        inputStream?.close()
//        outputStream.close()
//        file.absolutePath
//    } catch (e: Exception) {
//        e.printStackTrace()
//        null
//    }
//}

/**
 * Saves a Bitmap to a file and returns the file path.
 */
fun saveBitmapToFile(context: Context, bitmap: Bitmap?, fileName: String = "image.png"): String? {
    if (bitmap == null) return null
    return try {
        val file = File(context.cacheDir, fileName)
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        outputStream.flush()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun copyAssetsIfNotExist(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        val assetsToCopy = listOf(
            "model/phonelm_vocab.mllm",
            "model/qwen_vocab.mllm",
            "model/smollm_vocab.mllm",
            "model/phi3v_vocab.mllm",
            "model/qwen_merges.txt",
            "model/phonelm_merges.txt",
            "model/smollm_merges.txt",
            "model/llama2_hf_vocab.mllm"
        )

        val destinationDir = File(getDownloadsPath(context), "model")
        if (!destinationDir.exists()) {
            if (!destinationDir.mkdirs()) {
                Log.e("AssetCopy", "Failed to create directory: ${destinationDir.absolutePath}")
                return@withContext false
            }
        }

        var success = true
        for (asset in assetsToCopy) {
            try {
                val destinationFile = File(destinationDir, asset.substringAfterLast("/"))
                if (!destinationFile.exists()) {
                    context.assets.open(asset).use { inputStream ->
                        FileOutputStream(destinationFile).use { outputStream ->
                            copyStream(inputStream, outputStream)
                        }
                    }
                    Log.i("AssetCopy", "Copied: $asset to ${destinationFile.absolutePath}")
                } else {
                    Log.i("AssetCopy", "File already exists: ${destinationFile.absolutePath}")
                }
            } catch (e: Exception) {
                Log.e("AssetCopy", "Failed to copy: $asset", e)
                success = false
            }
        }
        return@withContext success
    }
}

fun copyStream(input: InputStream, output: FileOutputStream) {
    val buffer = ByteArray(1024)
    var bytesRead: Int
    while (input.read(buffer).also { bytesRead = it } != -1) {
        output.write(buffer, 0, bytesRead)
    }
}
fun getDownloadsPath(context: Context): String {
    return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath +"/"
//    return context.cacheDir.absolutePath + "/"
}