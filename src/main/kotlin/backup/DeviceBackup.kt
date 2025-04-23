package backup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import model.FileItem
import model.FileUtils
import runtime.adb.AdbDevicePoller
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.text.SimpleDateFormat
import java.util.*

class DeviceBackup (
    private val adbDevicePoller: AdbDevicePoller,
    private val coroutineScope: CoroutineScope
) {



    /*

    TODO:
    1. Add progress update to show to the user
    2. Fix backing up to folders with spaces
    3. Track the time it takes from start to end
    4. Add a tally of files backed-up out of total
    5. Add count of files that failed to backup
    6.



    ISSUES TO FIX

     */


    /**
     * Pull file to local device
     */
    fun backup(fileName: String
               , destinationPath: String = "Y:/OnePlusTest/"
        //, destinationPath: String = "M:/OnePlus 10T 12-04/"
               , onSuccess: () -> Unit) {
        log("Starting backup..")
        coroutineScope.launch {
            try {
                // Create a report
                // Create an index
                val report = mutableListOf<String>()
                val index = mutableListOf<String>()
                val failedBackups = mutableListOf<String>()

                // List the files in the source
                val sourceDir = AndroidTransferFile(coroutineScope, adbDevicePoller).apply {
                    name = "sdcard"
                    fullPath = "/sdcard/"
                }

                val destinationDir = PcTransferFile(coroutineScope, adbDevicePoller).apply {
                    name = destinationPath
                    fullPath = destinationPath
                }

                log("Source dir - [${sourceDir.fullPath}] & Destination dir [${destinationDir.fullPath}]")

                if (!sourceDir.exists())  {
                    log("Source dir does not exist")
                    return@launch
                }

                // Assume it's a directory
                val sourceDirList = sourceDir.listFiles()

                // Check if the destination exists and list the files
                if (!destinationDir.exists()) {
                    log("Destination dir does not exist")
                    return@launch
                }
                val destinationDirList = destinationDir.listFiles()

                // Have a queue and add both source and destination i.e source file & destination root folder
                val queue = LinkedList<Pair<AndroidTransferFile, PcTransferFile>>()

                /** TESTING **/

                val foldersToInclude = hashSetOf(
                    "/sdcard/Download/",
                    "/sdcard/Dukto/",
                    "/sdcard/Music/",
                    "/sdcard/Video/",
                    "/sdcard/mp3/",
                )

                /** END OF TESTING **/

                log("Queueing files in source directory...")
                for (file in sourceDirList) {
                    log("Adding to queue Pair< ${file.fullPath}, ${destinationDir.fullPath} > ")

                    if (foldersToInclude.contains(file.fullPath)) {
                        queue.offer(Pair(file as AndroidTransferFile, destinationDir))
                    }
                }

                // This will be a BFS
                // Process each queued item in a for loop
                log("Looping through the queue...")
                while (!queue.isEmpty()) {
                    val queueItem = queue.poll()
                    log("Processing -> Pair< (${queueItem.first.name}, ${queueItem.first.fullPath}) , (${queueItem.second.name}, ${queueItem.second.fullPath})>")
                    val androidFile = queueItem.first
                    val pcFile = queueItem.second

                    androidFile.addToIndex(index)
                    log("Added [${androidFile.name} - ${androidFile.fullPath}] to index")
                    if (!androidFile.exists()) {
                        log("Android file -> ${androidFile.name} - ${androidFile.fullPath} does not exist")
                        log("End of processing")
                        continue;
                    }

                    if (androidFile.isDirectory) {
                        log("Android file -> ${androidFile.name} - ${androidFile.fullPath} is a directory")
                        // TODO: FIX THIS
                        // Check if that child exists
                        val pcTargetFolder = File(pcFile.fullPath, androidFile.name)

                        log("Android file -> ${androidFile.name} - ${androidFile.fullPath} => child is ${pcTargetFolder.absolutePath}")
                        if (!pcTargetFolder.exists()) {
                            log("Child file does not exist -> Creating it and adding it to the report")
                            androidFile.addToReport(report)
                            val newDestinationDir = androidFile.makeDestinationDirectory(pcFile)

                            log("Android file -> ${androidFile.name} - ${androidFile.fullPath} : Adding children to the queue")
                            androidFile.queueChildren(queue, newDestinationDir)
                        } else {
                            log("Android file -> ${androidFile.name} - ${androidFile.fullPath} : Adding children to the queue")
                            androidFile.queueChildren(queue, pcTargetFolder.toPcTransferFile(coroutineScope, adbDevicePoller))
                        }
                    } else {
                        log("Android file -> ${androidFile.name} - ${androidFile.fullPath} : is not a directory")
                        // Update this to fetch the child pc file and compare the date modified and date created
                        if (!pcFile.containsChild(androidFile) || androidFile.hasChanged(pcFile)) {
                            log("Android file -> ${androidFile.name} - ${androidFile.fullPath} : has either changed or does not exist and needs to be backed up")
                            log("Android file -> ${androidFile.name} - ${androidFile.fullPath} : Adding to report")
                            androidFile.addToReport(report)
                            if (!androidFile.backup(pcFile)) {
                                failedBackups.add(androidFile.fullPath)
                            }
                        }
                    }
                }

                // When processing each item:
                // Add to the index
                // Check if the source exists
                // Check if the source is a directory
                // Check if the destination exists,
                // If it does, do nothing
                // else, create the directory & add it to the report
                // list the entries of the source and queue them up with the destination folder also
                // Else
                // Check if the destination exists
                // If it does, check if it has changed
                // If it has, add it to the report and back it up
                // else skip it
                // else
                // add it to the report
                // back it up


                /*
                                val dirPath = _directoryPath.joinToString("/")
                                adbDevicePoller.exec("pull /${dirPath}/${fileName} $destinationPath") { result ->
                                    if (result.any { it.contains("error") || it.contains("failed") }) {
                                        setError("Export file failed: ${result.joinToString("\n")}")
                                    } else {
                                        setSuccess("File exported successfully")
                                    }
                                    _isLoading.value = false
                                    onSuccess()
                                }*/

                log (" ")
                log (" ")
                log (" ")
                log("REPORT")
                log("-------------------")

                report.forEach {
                    log(it)
                }
                log (" ")
                log (" ")
                log (" ")

                log("INDEX")
                log("-------------------")

                index.forEach {
                    log(it)
                }

                log (" ")
                log (" ")
                log (" ")

                log("FAILED BACKUPS")
                log("-------------------")

                failedBackups.forEach {
                    log(it)
                }

                log (" ")
                log (" ")
                log (" ")



                log("SUMMARY")
                log("-------------------")

                log(String.format("Failed backups: %s/%s", failedBackups.size, index.size))
                log(String.format("Index total: %s", index.size))
                log(String.format("Files backed-up: %s/%s", report.size, index.size))

                log (" ")
                log (" ")
                log (" ")


            } catch (e: Exception) {
                /*setError("Export file failed: ${e.message}")
                _isLoading.value = false*/
            }
        }
    }

    fun traverseFiles(file: File) {

    }

    fun backupFiles(sourceTree: File, destinationFile: File) {

    }


}

class AndroidTransferFile(coroutineScope: CoroutineScope, adbDevicePoller: AdbDevicePoller) : TransferFile(coroutineScope,
    adbDevicePoller
) {

    fun addToReport(report: MutableList<String>) {
        report.add(fullPath)
    }

    fun addToIndex(index: MutableList<String>) {
        index.add(fullPath)
    }

    fun makeDestinationDirectory(pcDirectory: PcTransferFile) : PcTransferFile {
        val dir = File(pcDirectory.fullPath, name)
        dir.mkdirs()

        log("Created directories at ${dir.absolutePath} : IsDirectory = ${dir.isDirectory}")

        return PcTransferFile(coroutineScope, adbDevicePoller)
            .apply {
                isDirectory = true
                name = dir.name
                fullPath = dir.path
                dateCreated = Date()
                dateModified = Date()
            }
    }

    /* Adds the children of this folder to the queue. Uses the destinationFolder as the pair */
    suspend fun queueChildren(queue: LinkedList<Pair<AndroidTransferFile, PcTransferFile>>, destinationFolderPair: PcTransferFile) {
        log("Android file -> ${name} - ${fullPath} : queueing children")
        val children = listFiles()
        children.forEach { child ->
            log("Android file -> ${name} - ${fullPath} : queueing child -> <${child.name} - ${child.fullPath}>")
            queue.add(Pair(child as AndroidTransferFile, destinationFolderPair))
        }
    }

    /** Compares the android file & pc file i.e. size and possibly date changed. Only if android file has a newer date modified than the PC one **/
    fun hasChanged(pcFile: PcTransferFile): Boolean {
        val file = File(pcFile.fullPath, name)
        val fileCreatedDate = getCreatedDate(file)
        return ((size != file.length()) || (dateModified!!.time > file.lastModified()))
    }

    /* Copies the android file into the pc location*/
    suspend fun backup(pcFile: PcTransferFile): Boolean {
        log("Android file -> ${name} - ${fullPath} : backing it up to {${pcFile.fullPath}")
        log("backup for $fullPath to ${pcFile.fullPath}")

        if (DRY_RUN) {
            log("Android file -> ${name} - ${fullPath} : back-up skipped - DRY_RUN")
            return true
        }

        val result = adbDevicePoller.exec("pull '${fullPath.escapeForAdb()}' '${pcFile.fullPath.escapeForAdb()}'")
        log(result.joinToString("\n"))
        // 检查权限错误
        if (result.any { it.contains("error") || it.contains("failed") || it.contains("Permission denied")}) {
            //setError("权限不足：无法访问该目录")

            log("Android file -> ${name} - ${fullPath} : back-up failed. Skipping this")
            return false
        } else if (
            result.firstOrNull()?.startsWith("ls") == true ||
            result.lastOrNull()?.contains("Permission") == true ||
            result.lastOrNull()?.contains("directory") == true
        ) {
            log("Android file -> ${name} - ${fullPath} : back-up failed. Skipping this")
            return false
        } else {
            log("Android file -> ${name} - ${fullPath} : back-up succeeded")
            return true
        }
    }

    override suspend fun exists(): Boolean {
        val result = adbDevicePoller.exec("shell ls -l -p '${fullPath.escapeForAdb()}' | sort")
        log("exists for $fullPath")
        log(result.joinToString("\n"))
        // 检查权限错误
        if (result.any { it.contains("Permission denied") }) {
            //setError("权限不足：无法访问该目录")
            return false
        } else if (
            result.firstOrNull()?.startsWith("ls") == true ||
            result.lastOrNull()?.contains("Permission") == true ||
            result.lastOrNull()?.contains("directory") == true
        ) {
            return false
        } else {
            return true
        }
    }

    override suspend fun listFiles() : MutableList<out TransferFile> {
        // Added single quote to fix failing ls
        val result = adbDevicePoller.exec("shell ls -l -p '${fullPath.escapeForAdb()}' | sort")
        log("listFiles for $fullPath")
        log(result.joinToString("\n"))
        // 检查权限错误
        if (result.any { it.contains("Permission denied") }) {
            //setError("权限不足：无法访问该目录")
            return mutableListOf<AndroidTransferFile>()
        } else if (
            result.firstOrNull()?.startsWith("ls") == true ||
            result.lastOrNull()?.contains("Permission") == true ||
            result.lastOrNull()?.contains("directory") == true
        ) {
            return mutableListOf<AndroidTransferFile>()
        } else {
            val fileList = mutableListOf<AndroidTransferFile>()
            FileUtils.parseLsOutput(result).forEach {
                fileList.add(AndroidTransferFile(coroutineScope, adbDevicePoller).also { newFile ->
                    newFile.name = it.fileName
                    newFile.fullPath = "$fullPath${it.fileName}${getSeparator(it)}"
                    newFile.size = it.sizeInBytes
                    newFile.dateCreated = parseDate(it.date)
                    newFile.dateModified = parseDate(it.date)
                    newFile.isDirectory = it.isDir
                })
            }
            return fileList
        }
    }

    fun getSeparator(fileItem: FileItem) : String {
        return if (fileItem.isDir) "/" else ""
    }

    fun parseDate(dateString: String): Date {
        val dateTimeFormatter = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.ENGLISH)
        return dateTimeFormatter.parse(dateString)
    }
}

fun String.escapeForAdb(): String {
    return replace("(", "\\(")
        .replace(")", "\\)")
}

fun File.toPcTransferFile (coroutineScope: CoroutineScope, adbDevicePoller: AdbDevicePoller): PcTransferFile {

    return PcTransferFile(coroutineScope, adbDevicePoller)
        .also {
            it.isDirectory = isDirectory
            it.name = name
            it.fullPath = path
            it.dateCreated = getCreatedDate(this)
            it.dateModified = Date(lastModified())
        }
}
class PcTransferFile(coroutineScope: CoroutineScope, adbDevicePoller: AdbDevicePoller) : TransferFile(coroutineScope,
    adbDevicePoller
) {

    suspend fun containsChild(androidFile: AndroidTransferFile) : Boolean {
        listFiles()
            .forEach { file ->
                if (file.name.equals(androidFile.name))
                    return  true
            }

        return false
    }

    override suspend fun exists(): Boolean {
        return File(fullPath)
            .exists()
    }

    override suspend fun listFiles() : MutableList<out TransferFile> {
        val filesList = File(fullPath)
            .listFiles()

        return mutableListOf<PcTransferFile>().apply {
            filesList.forEach { file ->
                add(PcTransferFile(coroutineScope, adbDevicePoller).apply {
                    name = file.name
                    fullPath = "$fullPath/${file.name}"
                    size = file.length()
                    dateCreated =  getCreatedDate(file)
                    dateModified = Date(file.lastModified())
                    isDirectory = file.isDirectory
                })
            }
        }
    }
}

fun getCreatedDate(file: File) : Date {
    val attr: BasicFileAttributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
    return Date(attr.creationTime().toMillis())
}

abstract class TransferFile (coroutineScope: CoroutineScope, adbDevicePoller: AdbDevicePoller) {
    var name: String = ""
    var fullPath: String = ""
    var size : Long = 0
    var isDirectory : Boolean = false
    var dateModified: Date? = null
    var dateCreated: Date? = null
    var childFiles: MutableList<File> = mutableListOf()
    var childFilesSearchMap: HashMap<String, File>? = null

    val coroutineScope: CoroutineScope = coroutineScope
    val adbDevicePoller: AdbDevicePoller = adbDevicePoller

    abstract suspend fun exists() : Boolean

    // This should be performant enough and update the search map
    abstract suspend fun listFiles() : MutableList<out TransferFile>
}




val logFileDate = getFormattedDate("yyyy-MM-dd_HH_mm")
val logFile = File("C:/Users/Kigamba/Downloads/backup-log-$logFileDate.txt")
var DRY_RUN = true
fun log(tolog: String) {
    println(tolog)
    logToFile(logFile, tolog)
}

fun logToFile(file: File, tolog: String) {
    val fileWriter = FileWriter(file, true)

    val formattedDate = getFormattedDate("yyyy/MM/dd HH:mm")

    fileWriter.write(String.format("[ %s ] - %s", formattedDate, tolog))
    fileWriter.write("\n");
    fileWriter.flush()
}

fun getFormattedDate(format: String): String {
    val dateTimeFormatter = SimpleDateFormat(format, Locale.ENGLISH)
    return dateTimeFormatter.format(Date())
}