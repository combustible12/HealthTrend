package android.content
import android.net.Uri
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
/** Disk-backed JVM test double; Android cache/lifecycle semantics require device validation. */
class Context(val root:File=java.nio.file.Files.createTempDirectory("healthtrend-synthetic-").toFile()) {
 companion object {const val MODE_PRIVATE=0}
 val filesDir=File(root,"files").apply{mkdirs()}
 val cacheDir=File(root,"cache").apply{mkdirs()}
 val applicationInfo=ApplicationInfo(root.path)
 val contentResolver=ContentResolver()
 private val preferences=mutableMapOf<String,SharedPreferences>()
 fun getSharedPreferences(name:String,mode:Int)=preferences.getOrPut(name){SharedPreferences(File(root,"shared_prefs/$name.xml"))}
}
class ApplicationInfo(val dataDir:String)
class ContentResolver {
 fun openInputStream(uri:Uri):java.io.InputStream?=File(uri.path!!).inputStream()
 fun openOutputStream(uri:Uri,mode:String):java.io.OutputStream?=File(uri.path!!).outputStream()
}
class SharedPreferences(private val file:File){
 val all=linkedMapOf<String,Any>()
 init{if(file.exists()){
  val nodes=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
  for(i in 0 until nodes.length){val n=nodes.item(i);if(n.nodeName=="string")all[n.attributes.getNamedItem("name").nodeValue]=n.textContent}
 }}
 fun contains(k:String)=all.containsKey(k)
 fun getString(k:String,d:String?):String?=all[k] as? String?:d
 fun getBoolean(k:String,d:Boolean)=all[k] as? Boolean?:d
 fun edit()=Editor(this)
 private fun save(){
  file.parentFile.mkdirs()
  val d=DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();val root=d.createElement("map");d.appendChild(root)
  all.forEach{(k,v)->val e=d.createElement("string");e.setAttribute("name",k);e.textContent=v.toString();root.appendChild(e)}
  javax.xml.transform.TransformerFactory.newInstance().newTransformer().transform(javax.xml.transform.dom.DOMSource(d),javax.xml.transform.stream.StreamResult(file))
 }
 class Editor(private val p:SharedPreferences){
  private val changes=linkedMapOf<String,Any?>()
  fun putString(k:String,v:String?)=apply{changes[k]=v}
  fun putBoolean(k:String,v:Boolean)=apply{changes[k]=v}
  fun putInt(k:String,v:Int)=apply{changes[k]=v}
  fun putLong(k:String,v:Long)=apply{changes[k]=v}
  fun remove(k:String)=apply{changes[k]=null}
  fun commit():Boolean {changes.forEach{(k,v)->if(v==null)p.all.remove(k)else p.all[k]=v};p.save();return true}
 }
}
