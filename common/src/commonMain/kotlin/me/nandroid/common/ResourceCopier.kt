package me.nandroid.common

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList
import org.xml.sax.InputSource
import java.io.File
import java.io.StringReader
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMResult
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

enum class ResourceTypes {
    STRING {
        override val defaultFileContent = "<resources xmlns:xliff=\"urn:oasis:names:tc:xliff:document:1.2\"></resources>"
        override val tag: String = "string"
        override fun toString() = "strings.xml"
    },
    DIMEN {
        override val defaultFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        override val tag = "dimen"
        override fun toString() = "dimens.xml"
    },
    DIMEN_ITEM {
        override val defaultFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        override val tag = "item"
        override fun toString() = "dimens.xml"
    },
    DECLARE_STYLEABLE {
        override val defaultFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        override val tag = "declare-styleable"
        override fun toString() = "attrs.xml"
    },
    COLOR {
        override val defaultFileContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>"
        override val tag: String = "color"
        override fun toString() = "colors.xml"
    };
    abstract val defaultFileContent: String
    abstract val tag: String
}

interface ResourceCopiersInput{
    val sourceResourceParentPath: String
    val destinationResourceParentPath: String
    val resourceNames: Array<String>
    val resourceType: ResourceTypes
}

class ResourceCopier(input: ResourceCopiersInput) {

    private val sourceResourceParentPath = input.sourceResourceParentPath
    private val destinationResourceParentPath = input.destinationResourceParentPath
    private val resourceNames = input.resourceNames
    private val resourceType = input.resourceType

    fun invoke() {
        val stringFiles = getStringsXmlFiles(sourceResourceParentPath)
        val results = stringFiles.map { file ->
            Pair(file.path.replace(sourceResourceParentPath, ""), parseXML(file, resourceNames))
        }
        results.forEach {
            appendElementsToXML(it.first, it.second)
        }
    }

    private fun getStringsXmlFiles(path: String): List<File> {
        val files = mutableListOf<File>()

        val file = File(path)
        if (file.exists() && file.isDirectory) {
            file.listFiles()?.forEach {
                if (it.isFile && it.name == "$resourceType") {
                    files.add(it)
                } else if (it.isDirectory) {
                    files.addAll(getStringsXmlFiles(it.absolutePath))
                }
            }
        }

        return files
    }

    private fun parseXML(file: File, resourceNames: Array<String>): List<String> {
        val documentBuilderFactory = DocumentBuilderFactory.newInstance()
        val documentBuilder = documentBuilderFactory.newDocumentBuilder()
        val document = documentBuilder.parse(file)

        document.documentElement.normalize()

        val nodeList: NodeList = document.getElementsByTagName(resourceType.tag)
        val elements = mutableListOf<String>()

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i)
            if (node.nodeType == Node.ELEMENT_NODE) {
                val element = node as Element
                elements.addAll(resourceNames.mapNotNull { resourceName ->
                    if (element.getAttribute("name") == resourceName) {
                        elementToXmlString(element, false)
                    } else {
                        null
                    }
                })
            }
        }
        return elements
    }

    private fun elementToXmlString(element: Node, isIndentNeeded: Boolean = false): String {
        val transformerFactory = TransformerFactory.newInstance()
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes")
        if (isIndentNeeded) {
            transformer.setOutputProperty(OutputKeys.INDENT, "yes") // Enable indentation
            transformer.setOutputProperty(
                "{http://xml.apache.org/xslt}indent-amount",
                "1"
            ) // Set the indentation amount
            transformer.setOutputProperty(OutputKeys.METHOD, "xml") // Set the output method to XML
        }
        val source = DOMSource(element)
        val writer = StringWriter()
        val result = StreamResult(writer)
        transformer.transform(source, result)

        return writer.toString()
    }

    private fun parseXmlString(xmlString: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isIgnoringElementContentWhitespace = true
        val builder = factory.newDocumentBuilder()
        val document = builder.parse(InputSource(StringReader(xmlString)))
        removeNewlines(document)
        return document
    }

    private fun importNodeToDocument(document: Document, node: Node): Node {
        return document.importNode(node, true)
    }

    private fun removeNewlines(node: Node) {
        val childNodes = node.childNodes
        var i = 0
        while (i < childNodes.length) {
            val child = childNodes.item(i)
            if (child.nodeType == Node.TEXT_NODE) {
                child.nodeValue = child.nodeValue?.replace("\n", "")?.replace("    ", "")?.trim()
                i++
            } else {
                removeNewlines(child)
                i++
            }
        }
    }

    private fun appendElementsToXML(fileName: String, elements: List<String>) {
        val fileAbsolutePath = destinationResourceParentPath + fileName
        val folder = File(destinationResourceParentPath + fileName.replace("/$resourceType", ""))
        if (!folder.exists()) {
            folder.mkdir()
        }
        println("Writing to $fileAbsolutePath")
        val file = File(fileAbsolutePath)
        if (!file.exists()) {
            println("appendElementsToXML: creating.. $fileAbsolutePath")
            file.createNewFile()
            file.writeText(resourceType.defaultFileContent)
        }
        var fileContent = parseXmlString(file.readText())
        elements.forEach { element ->
            val contentToBeWritten = parseXmlString(element)
            val rootElement = fileContent.documentElement
            val importedNode = importNodeToDocument(fileContent, contentToBeWritten.documentElement)
            rootElement.appendChild(importedNode)
            val transformerFactory = TransformerFactory.newInstance()
            val transformer = transformerFactory.newTransformer()
            val result = DOMResult()
            transformer.transform(DOMSource(fileContent), result)
            val updatedXmlString = result.node
            fileContent = updatedXmlString as Document
        }
        file.writeText(elementToXmlString(fileContent, true))
    }
}