package liyin.party.mipushstate

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.widget.CardView
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewManager
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import org.jetbrains.anko.*
import org.jetbrains.anko.coroutines.experimental.bg
import org.jetbrains.anko.custom.ankoView
import org.jetbrains.anko.support.v4.onRefresh
import java.nio.charset.Charset

class MainActivity : Activity() {
    private val allList = mutableListOf<AppInfoMi>()
    private lateinit var cAdapter: CAdapter
    var fakeInstalled = false
    var fakeModule = false
    private lateinit var refreshLayout: SwipeRefreshLayout

    data class AppInfoMi(val pack: String, val name: String, val state: Boolean)

    open class IDS {
        companion object {
            const val titleID = 0xE123
            const val stateID = 0xE124
            const val cardID = 0xE125
            const val frameID = 0xE126
        }
    }

    private inline fun ViewManager.recyclerLayout(init: RecyclerView.() -> Unit): RecyclerView = ankoView({ RecyclerView(it) }, theme = 0, init = init)
    private inline fun ViewManager.swipeRefreshLayout(init: SwipeRefreshLayout.() -> Unit): SwipeRefreshLayout = ankoView({ SwipeRefreshLayout(it) }, theme = 0, init = init)

    private fun Context.showCenterToast(resId: Int, duration: Int) {
        Toast.makeText(this, getText(resId), duration).let {
            val textview_id = Resources.getSystem().getIdentifier("message", "id", "android")
            (it.view.findViewById(textview_id) as TextView).gravity = Gravity.CENTER
            it.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cAdapter = CAdapter(this@MainActivity, allList)
        verticalLayout {
            refreshLayout = swipeRefreshLayout {
                isRefreshing = true
                setColorSchemeColors(Color.parseColor("#4285F4"), Color.parseColor("#EA4335"), Color.parseColor("#FBBC05"), Color.parseColor("#34A853"))
                onRefresh {
                    getAllState()
                }
                recyclerLayout {
                    padding = dip(8)
                    layoutManager = LinearLayoutManager(this@MainActivity)
                    adapter = cAdapter
                }.lparams(height = matchParent, width = matchParent) {
                    margin = dip(8)
                }
            }
        }
        bg {
            if (getXMSFInstalled()) {
                fakeInstalled = true
                if (getProperty("ro.miui.ui.version.name", "") != "" && getProperty("ro.miui.ui.version.code", "") != "") {
                    fakeModule = true
                    runOnUiThread {
                        showCenterToast(R.string.descr, Toast.LENGTH_LONG)
                    }
                }else{
                    fakeModule = false
                    runOnUiThread {
                        showCenterToast(R.string.descr_try, Toast.LENGTH_LONG)
                    }
                }
            }
            getAllState()
        }
    }

    private fun getAllState() {
        async(UI) {
            val data: Deferred<List<AppInfoMi>> = bg {
                getInfo()
            }
            showInfo(data.await())
        }
    }

    @Suppress("DEPRECATION")
    private fun getXMSFInstalled(): Boolean =
            packageManager.getInstalledPackages(PackageManager.GET_DISABLED_COMPONENTS).any { it.packageName == "com.xiaomi.xmsf" }


    private fun getInfo(): List<AppInfoMi> {
        val list = mutableListOf<AppInfoMi>()
        @Suppress("DEPRECATION")
        packageManager.getInstalledPackages(PackageManager.GET_SERVICES or PackageManager.GET_DISABLED_COMPONENTS).forEach {
            try {
                it.services?.find { it.name == "com.xiaomi.push.service.XMPushService" }.takeUnless { it == null }?.let {
                    val pName = it.packageName
                    val rName = packageManager.getApplicationLabel(packageManager.getApplicationInfo(pName, 0)).toString()
                    val miEnabled = (packageManager.getComponentEnabledSetting(ComponentName(pName, "com.xiaomi.push.service.XMPushService")) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
                    list.add(AppInfoMi(pName, rName, miEnabled))
                }
            } catch (ignore: Exception) {
            }
        }
        return list.toList()
    }

    private fun showInfo(data: List<AppInfoMi>) {
        allList.clear()
        allList.addAll(data)
        cAdapter.notifyDataSetChanged()
        refreshLayout.isRefreshing = false
    }

    override fun onResume() {
        super.onResume()
        getAllState()
    }

    private fun getProperty(name: String, defaultValue: String): String =
            try {
                Runtime.getRuntime().exec("getprop $name").inputStream.bufferedReader(Charset.defaultCharset()).use {
                    it.readLines().joinToString().trim()
                }
            } catch (e: Exception) {
                defaultValue
            }


    class CAdapter(val context: MainActivity, private val list: List<AppInfoMi>) : RecyclerView.Adapter<CAdapter.CViewHolder>() {
        fun getFakedInstall(isColor: Boolean): Int = when (context.fakeInstalled) {
            true -> if (isColor) Color.parseColor("#FFA31A") else if(context.fakeModule) R.string.stateOnClickToOff else R.string.stateOnClickTryToOff
            false -> if (isColor) Color.GREEN else R.string.stateOn
        }

        override fun onBindViewHolder(holder: CViewHolder?, position: Int) {
            list[position].let { (pack, name, state) ->
                println(pack)
                holder?.title?.text = name
                holder?.state?.text = context.getText(when (state) {
                    true -> {
                        if (pack == "com.xiaomi.xmsf") {
                            R.string.stateFrameworkOn
                        } else {
                            getFakedInstall(false)
                        }
                    }
                    false -> {
                        if (pack == "com.xiaomi.xmsf") {
                            R.string.stateFrameworkOff
                        } else {
                            R.string.stateOff
                        }
                    }
                })
                holder?.state?.textColor = when (state) {
                    true -> {
                        if (pack == "com.xiaomi.xmsf") {
                            Color.GRAY
                        } else {
                            getFakedInstall(true)
                        }
                    }
                    false -> {
                        Color.RED
                    }
                }
                holder?.card?.setOnClickListener {
                    val intent = context.packageManager.getLaunchIntentForPackage(pack)
                    context.startActivity(intent)
                }
                holder?.frame?.setOnClickListener {
                    val intent = context.packageManager.getLaunchIntentForPackage(pack)
                    context.startActivity(intent)
                }
            }
        }

        override fun getItemCount(): Int = list.size

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): CViewHolder =
                CViewHolder(ItemUI(context).createView(AnkoContext.Companion.create(parent!!.context, parent)))

        class CViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.find(IDS.titleID)
            val state: TextView = itemView.find(IDS.stateID)
            val card: CardView = itemView.find(IDS.cardID)
            val frame: FrameLayout = itemView.find(IDS.frameID)
        }

        class ItemUI(private val context: Context) : AnkoComponent<ViewGroup> {

            private inline fun ViewManager.cardView(init: CardView.() -> Unit): CardView = ankoView({ CardView(it) }, theme = 0, init = init)

            override fun createView(ui: AnkoContext<ViewGroup>): View {
                val outValue = TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
                return with(ui) {
                    frameLayout {
                        id = IDS.frameID
                        layoutParams = ViewGroup.MarginLayoutParams(ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)).apply { this.setMargins(8, 8, 8, 8) }
                        cardView {
                            padding = dip(8)
                            cardElevation = 8f
                            id = IDS.cardID
                            foreground = resources.getDrawable(outValue.resourceId, context.theme)
                            relativeLayout {
                                textView {
                                    padding = dip(8)
                                    id = IDS.titleID
                                }.lparams {
                                    alignParentLeft()
                                }
                                textView {
                                    padding = dip(8)
                                    id = IDS.stateID
                                }.lparams {
                                    alignParentRight()
                                }
                            }.lparams(width = matchParent, height = wrapContent) {
                                margin = dip(8)
                            }
                        }.lparams(width = matchParent, height = wrapContent) {
                            margin = dip(8)
                        }
                    }
                }
            }
        }
    }
}
