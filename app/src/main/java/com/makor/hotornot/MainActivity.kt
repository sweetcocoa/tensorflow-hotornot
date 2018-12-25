package com.makor.hotornot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem

import com.makor.hotornot.classifier.*
import com.makor.hotornot.classifier.tensorflow.ImageClassifierFactory
import com.makor.hotornot.utils.getCroppedBitmap
import com.makor.hotornot.utils.getUriFromFilePath
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

import android.widget.Toast
import java.net.URI

private const val REQUEST_PERMISSIONS = 1
private const val REQUEST_TAKE_PICTURE = 2
private const val REQUEST_IMPORT_IMAGE = 3

class MainActivity : AppCompatActivity() {

    private val handler = Handler()
    private lateinit var classifier: Classifier
    private var photoFilePath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissions()
    }

    private fun checkPermissions() {
        if (arePermissionsAlreadyGranted()) {
            init()
        } else {
            requestPermissions()
        }
    }

    private fun arePermissionsAlreadyGranted() =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_PERMISSIONS)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_PERMISSIONS && arePermissionGranted(grantResults)) {
            init()
        } else {
            requestPermissions()
        }
    }

    private fun arePermissionGranted(grantResults: IntArray) =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED

    private fun init() {
        createClassifier()
        takePhoto()
    }

    private fun createClassifier() {
        classifier = ImageClassifierFactory.create(
                assets,
                GRAPH_FILE_PATH,
                LABELS_FILE_PATH,
                IMAGE_SIZE,
                GRAPH_INPUT_NAME,
                GRAPH_OUTPUT_NAME
        )
    }

    private fun takePhoto() {
        photoFilePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath + "/${System.currentTimeMillis()}.jpg"
        val currentPhotoUri = getUriFromFilePath(this, photoFilePath)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
        takePictureIntent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

        if (takePictureIntent.resolveActivity(packageManager) != null) {
            startActivityForResult(takePictureIntent, REQUEST_TAKE_PICTURE)
        }
    }

    private fun importPhoto() {

        var importPictureIntent = Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        importPictureIntent.type = "image/*"

        if (importPictureIntent.resolveActivity(packageManager) != null) {
//            importPictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, importPictureIntent!!.data)
//            photoFilePath = importPictureIntent.data.path
            startActivityForResult(importPictureIntent, REQUEST_IMPORT_IMAGE)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.activity_main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == R.id.take_photo) {
            takePhoto()
            true
        } else if (item.itemId == R.id.import_photo){
            importPhoto()
            true
        }
        else {
            super.onOptionsItemSelected(item)
        }
    }

    fun getRealPathFromURI(uri: Uri): String {
        val cursor = contentResolver.query(uri, null, null, null, null)
        cursor!!.moveToFirst()
        val idx = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
        return cursor.getString(idx)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        if (requestCode == REQUEST_TAKE_PICTURE) {
            val file = File(photoFilePath)
            if (file.exists()) {
                classifyPhoto(file)
            }
        }
        else if (requestCode == REQUEST_IMPORT_IMAGE) {
            if (data != null) {
                photoFilePath= getRealPathFromURI(data.data)
                val file = File(photoFilePath)
                if (file.exists()) {
                    Toast.makeText(this, "Open Success : " + photoFilePath,  Toast.LENGTH_LONG).show()
                    classifyPhoto(file)
                }
                else {
                    Toast.makeText(this, "File open failed : " + photoFilePath,  Toast.LENGTH_LONG).show()
                }



            }
        }




//        if (file.exists()) {
//            if (requestCode == REQUEST_TAKE_PICTURE) {
//                classifyPhoto(file)
//            }
//            else if (requestCode == REQUEST_IMPORT_IMAGE) {
//                classifyPhoto(file)
//                Toast.makeText(this, "성공적으로 Image Import 되었다.",  Toast.LENGTH_LONG).show()
//            }
//        }
//        else {
//            if (requestCode == REQUEST_IMPORT_IMAGE) {
//                Toast.makeText(this, "존재하지 않는 이미지 파일이다..",  Toast.LENGTH_LONG).show()
//            }
//        }
    }

    private fun classifyPhoto(file: File) {
        val photoBitmap = BitmapFactory.decodeFile(file.absolutePath)
        val croppedBitmap = getCroppedBitmap(photoBitmap)
        classifyAndShowResult(croppedBitmap)
        imagePhoto.setImageBitmap(photoBitmap)
    }

    private fun classifyAndShowResult(croppedBitmap: Bitmap) {
        runInBackground(
                Runnable {
                    val result = classifier.recognizeImage(croppedBitmap)
//                    val result = Result(result = 15f.toString(), confidence = 0.7f)
                    showResult(result)
                })
    }

    @Synchronized
    private fun runInBackground(runnable: Runnable) {
        handler.post(runnable)
    }

    private fun showResult(result: Result) {
        textResult.text = result.result.toUpperCase()
        layoutContainer.setBackgroundColor(getColorFromResult(result.result))
    }

    @Suppress("DEPRECATION")
    private fun getColorFromResult(result: String): Int {
        return if (result == getString(R.string.hot)) {
            resources.getColor(R.color.hot)
        } else {
            resources.getColor(R.color.not)
        }
    }
}
