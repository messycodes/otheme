package com.chuishui.otheme

import android.util.Log
import org.w3c.dom.Element
import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

object ThemeParser {
    private const val TAG = "ThemeParser"
    
    fun parseThemeInfoFromXml(xmlContent: String): ThemeInfo? {
        return try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(xmlContent.byteInputStream())
            
            document.documentElement.normalize()
            
            val root = document.documentElement
            
            ThemeInfo(
                author = getTagValue("Author", root),
                summary = getTagValue("Summary", root),
                description = getTagValue("Description", root),
                lastModifyTime = getTagValue("LastModifyTime", root),
                versionName = getTagValue("VersionName", root),
                versionCode = getTagValue("VersionCode", root),
                editorVersion = getTagValue("EditorVersion", root),
                standardVersion = getTagValue("StandardVersion", root),
                uuid = getTagValue("UUID", root),
                packageName = getTagValue("PackageName", root)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing theme info from XML string", e)
            null
        }
    }
    
    fun parseThemeInfo(themeFile: File): ThemeInfo? {
        return try {
            ZipFile(themeFile).use { zip ->
                val entry = zip.getEntry("themeInfo.xml") ?: return null
                
                val factory = DocumentBuilderFactory.newInstance()
                val builder = factory.newDocumentBuilder()
                val document = builder.parse(zip.getInputStream(entry))
                
                document.documentElement.normalize()
                
                val root = document.documentElement
                
                ThemeInfo(
                    author = getTagValue("Author", root),
                    summary = getTagValue("Summary", root),
                    description = getTagValue("Description", root),
                    lastModifyTime = getTagValue("LastModifyTime", root),
                    versionName = getTagValue("VersionName", root),
                    versionCode = getTagValue("VersionCode", root),
                    editorVersion = getTagValue("EditorVersion", root),
                    standardVersion = getTagValue("StandardVersion", root),
                    uuid = getTagValue("UUID", root),
                    packageName = getTagValue("PackageName", root)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing theme info", e)
            null
        }
    }
    
    private fun getTagValue(tag: String, element: Element): String {
        return try {
            val nodeList = element.getElementsByTagName(tag)
            if (nodeList.length > 0) {
                nodeList.item(0).textContent.trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
