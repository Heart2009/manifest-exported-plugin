package com.xiachufang.manifest.exported

import groovy.namespace.QName
import groovy.util.Node
import groovy.util.NodeList
import groovy.xml.XmlParser
import java.io.File
import java.io.PrintWriter
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * 在processDebugMainManifest之前对manifest进行处理
 * @author petterp To 2022/6/21
 */
open class AddExportMainManifestTask : DefaultTask() {

    private lateinit var extArg: ExportedExtension

    // mainManifest
    private lateinit var mainManifest: File

    // 第三方aar的Manifest
    private lateinit var manifests: Set<File>

    private val qNameKey =
        QName("http://schemas.android.com/apk/res/android", "name", "android")
    private val qExportedKey =
        QName("http://schemas.android.com/apk/res/android", "exported", "android")

    fun setManifests(files: Set<File>) {
        this.manifests = files
    }

    fun setMainManifest(mainManifest: File) {
        this.mainManifest = mainManifest
    }

    fun setExtArg(arg: ExportedExtension) {
        this.extArg = arg
    }

    @TaskAction
    fun action() {
        println("-----exported->start------")
        // 默认不对主manifest做处理,交给系统自行处理,主要原因是这里是我们业务可控制部分
        if (extArg.enableMainManifest)
            exportedManifest(mainManifest)
        manifests.forEach {
            exportedManifest(it)
        }
        println("-----exported->End--------")
    }

    private fun exportedManifest(file: File) {
        val aarName = file.parentFile.name
        extArg.log("开始处理[$aarName]")
        extArg.log("path: [${file.path}]")
        if (!file.exists()) {
            extArg.log("[$aarName]不存在")
            return
        }
        val xml = XmlParser().parse(file)
        // 先拿出appNode
        val applicationNode = xml.children()[0] as Node
        // 过滤使用 intent 过滤器的 activity、服务 或 广播接收器 && exported未显式声明
        val nodes = applicationNode.children().asSequence().map {
            it as Node
        }.filter { it ->
            val name = it.name()
            (name == "activity" || name == "receiver" || name == "service") &&
                it.attribute(qExportedKey) == null && it.nodeList().any {
                it.name() == "intent-filter"
            }
        }.toList().takeIf {
            it.isNotEmpty()
        }
        if (nodes === null) {
            extArg.log("[$aarName] 未匹配可以更改的,跳过")
            return
        }

        // 开始自定义逻辑,目前只判断action部分即可
        nodes.forEach { it ->
            val isExported = it.nodeList().any { node ->
                node.nodeList().any {
                    it.name() == "action" && it.anyTag(qNameKey, values = extArg.actionRules)
                }
            }
            it.attributes()["android:exported"] = "$isExported"
        }
        val pw = PrintWriter(file)
        pw.write(groovy.xml.XmlUtil.serialize(xml))
        pw.close()
    }

    private fun Node.nodeList() = (this.value() as NodeList).map { it as Node }

    private fun Node.anyTag(key: QName, vararg values: String) =
        attributes()[key]?.let { value ->
            values.any {
                it == value.toString()
            }
        } ?: false
}
