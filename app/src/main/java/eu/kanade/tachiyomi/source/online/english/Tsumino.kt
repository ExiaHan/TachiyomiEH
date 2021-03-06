package eu.kanade.tachiyomi.source.online.english

import android.net.Uri
import com.github.salomonbrys.kotson.*
import com.google.gson.JsonParser
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.LewdSource
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import exh.TSUMINO_SOURCE_ID
import exh.metadata.models.Tag
import exh.metadata.models.TsuminoMetadata
import exh.metadata.models.TsuminoMetadata.Companion.BASE_URL
import exh.util.urlImportFetchSearchManga
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.*

class Tsumino: ParsedHttpSource(), LewdSource<TsuminoMetadata, Document> {
    override val id = TSUMINO_SOURCE_ID
    
    override val lang = "en"
    override val supportsLatest = true
    override val name = "Tsumino"
    
    override fun queryAll() = TsuminoMetadata.EmptyQuery()
    
    override fun queryFromUrl(url: String) = TsuminoMetadata.UrlQuery(url)
    
    override val baseUrl = BASE_URL
    
    override val metaParser: TsuminoMetadata.(Document) -> Unit = {
        url = it.location()
        tags.clear()
        
        it.getElementById("Title")?.text()?.let {
            title = it.trim()
        }
        
        it.getElementById("Artist")?.children()?.first()?.text()?.trim()?.let {
            tags.add(Tag("artist", it, false))
            artist = it
        }
    
        it.getElementById("Uploader")?.children()?.first()?.text()?.trim()?.let {
            tags.add(Tag("uploader", it, false))
            uploader = it
        }
        
        it.getElementById("Uploaded")?.text()?.let {
            uploadDate = TM_DATE_FORMAT.parse(it.trim()).time
        }
        
        it.getElementById("Pages")?.text()?.let {
            length = it.trim().toIntOrNull()
        }
        
        it.getElementById("Rating")?.text()?.let {
            ratingString = it.trim()
        }
    
        it.getElementById("Category")?.children()?.first()?.text()?.let {
            category = it.trim()
            tags.add(Tag("genre", it, false))
        }
        
        it.getElementById("Collection")?.children()?.first()?.text()?.let {
            collection = it.trim()
        }
        
        it.getElementById("Group")?.children()?.first()?.text()?.let {
            group = it.trim()
            tags.add(Tag("group", it, false))
        }
        
        parody.clear()
        it.getElementById("Parody")?.children()?.forEach {
            val entry = it.text().trim()
            parody.add(entry)
            tags.add(Tag("parody", entry, false))
        }
        
        character.clear()
        it.getElementById("Character")?.children()?.forEach {
            val entry = it.text().trim()
            character.add(entry)
            tags.add(Tag("character", entry, false))
        }
        
        it.getElementById("Tag")?.children()?.let {
            tags.addAll(it.map {
                Tag("tag", it.text().trim(), false)
            })
        }
    }
    
    fun genericMangaParse(response: Response): MangasPage {
        val json = jsonParser.parse(response.body()!!.string()!!).asJsonObject
        val hasNextPage = json["PageNumber"].int < json["PageCount"].int
        
        val manga = json["Data"].array.map {
            val obj = it.obj["Entry"].obj
            
            SManga.create().apply {
                val id = obj["Id"].long
                setUrlWithoutDomain(TsuminoMetadata.mangaUrlFromId(id.toString()))
                thumbnail_url = TsuminoMetadata.thumbUrlFromId(id.toString())
                
                title = obj["Title"].string
            }
        }
        
        return MangasPage(manga, hasNextPage)
    }
    
    fun genericMangaRequest(page: Int,
                            query: String,
                            sort: SortType,
                            length: LengthType,
                            minRating: Int,
                            excludeParodies: Boolean = false,
                            advSearch: List<AdvSearchEntry> = emptyList())
        = POST("$BASE_URL/Books/Operate", body = FormBody.Builder()
            .add("PageNumber", (page + 1).toString())
            .add("Text", query)
            .add("Sort", sort.name)
            .add("List", "0")
            .add("Length", length.id.toString())
            .add("MinimumRating", minRating.toString())
            .apply {
                advSearch.forEachIndexed { index, entry ->
                    add("Tags[$index][Type]", entry.type.toString())
                    add("Tags[$index][Text]", entry.text)
                    add("Tags[$index][Exclude]", entry.exclude.toString())
                }
                
                if(excludeParodies)
                    add("Exclude[]", "6")
            }
            .build())
    
    enum class SortType {
        Newest,
        Oldest,
        Alphabetical,
        Rating,
        Pages,
        Views,
        Random,
        Comments,
        Popularity
    }
    
    enum class LengthType(val id: Int) {
        Any(0),
        Short(1),
        Medium(2),
        Long(3)
    }
    
    override fun popularMangaSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun popularMangaRequest(page: Int) = genericMangaRequest(page,
            "",
            SortType.Random,
            LengthType.Any,
            0)
    
    override fun popularMangaParse(response: Response) = genericMangaParse(response)
    
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun latestUpdatesRequest(page: Int) = genericMangaRequest(page,
            "",
            SortType.Newest,
            LengthType.Any,
            0)
    override fun latestUpdatesParse(response: Response) = genericMangaParse(response)
    
    //Support direct URL importing
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList) =
            urlImportFetchSearchManga(query, {
                super.fetchSearchManga(page, query, filters)
            })
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Append filters again, to provide fallback in case a filter is not provided
        // Since we only work with the first filter when building the result, if the filter is provided,
        // the original filter is ignored
        val f = filters + getFilterList()
        
        return genericMangaRequest(
                page,
                query,
                SortType.values()[f.filterIsInstance<SortFilter>().first().state],
                LengthType.values()[f.filterIsInstance<LengthFilter>().first().state],
                f.filterIsInstance<MinimumRatingFilter>().first().state,
                f.filterIsInstance<ExcludeParodiesFilter>().first().state,
                f.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
                    val splitState = filter.state.split(",").map(String::trim).filterNot(String::isBlank)
                    
                    splitState.map {
                        AdvSearchEntry(filter.type, it.removePrefix("-"), it.startsWith("-"))
                    }
                }
        )
    }
    
    override fun searchMangaSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")
    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun searchMangaParse(response: Response) = genericMangaParse(response)
    
    override fun mangaDetailsParse(document: Document)
            = parseToManga(queryFromUrl(document.location()), document)
    
    override fun chapterListSelector() = throw UnsupportedOperationException("Unused method called!")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Unused method called!")
    override fun fetchChapterList(manga: SManga) = lazyLoadMeta(queryFromUrl(manga.url),
            client.newCall(mangaDetailsRequest(manga)).asObservableSuccess().map { it.asJsoup() }
    ).map {
        listOf(
                SChapter.create().apply {
                    url = "/Read/View/${it.tmId}"
                    name = "Chapter"
                    
                    it.uploadDate?.let { date_upload = it }
                    
                    chapter_number = 1f
                }
        )
    }
    
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val id = chapter.url.substringAfterLast('/')
        val call = POST("$BASE_URL/Read/Load", body = FormBody.Builder().add("q", id).build())
        return client.newCall(call).asObservableSuccess().map {
            val parsed = jsonParser.parse(it.body()!!.string()).obj
            val pageUrls = parsed["reader_page_urls"].array
            
            val imageUrl = Uri.parse("$BASE_URL/Image/Object")
            pageUrls.mapIndexed { index, obj ->
                val newImageUrl = imageUrl.buildUpon().appendQueryParameter("name", obj.string)
                Page(index, chapter.url + "#${index + 1}", newImageUrl.toString())
            }
        }
    }
    
    override fun pageListParse(document: Document) = throw UnsupportedOperationException("Unused method called!")
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException("Unused method called!")
    
    data class AdvSearchEntry(val type: Int, val text: String, val exclude: Boolean)
    
    override fun getFilterList() = FilterList(
            Filter.Header("Separate tags with commas"),
            Filter.Header("Prepend with dash to exclude"),
            TagFilter(),
            CategoryFilter(),
            CollectionFilter(),
            GroupFilter(),
            ArtistFilter(),
            ParodyFilter(),
            CharactersFilter(),
            UploaderFilter(),
            
            Filter.Separator(),
            
            SortFilter(),
            LengthFilter(),
            MinimumRatingFilter(),
            ExcludeParodiesFilter()
    )
    
    class TagFilter : AdvSearchEntryFilter("Tags", 1)
    class CategoryFilter : AdvSearchEntryFilter("Categories", 2)
    class CollectionFilter : AdvSearchEntryFilter("Collections", 3)
    class GroupFilter : AdvSearchEntryFilter("Groups", 4)
    class ArtistFilter : AdvSearchEntryFilter("Artists", 5)
    class ParodyFilter : AdvSearchEntryFilter("Parodies", 6)
    class CharactersFilter : AdvSearchEntryFilter("Characters", 7)
    class UploaderFilter : AdvSearchEntryFilter("Uploaders", 8)
    open class AdvSearchEntryFilter(name: String, val type: Int) : Filter.Text(name)
    
    class SortFilter : Filter.Select<SortType>("Sort by", SortType.values())
    class LengthFilter : Filter.Select<LengthType>("Length", LengthType.values())
    class MinimumRatingFilter : Filter.Select<String>("Minimum rating", (0 .. 5).map { "$it stars" }.toTypedArray())
    class ExcludeParodiesFilter : Filter.CheckBox("Exclude parodies")
    
    companion object {
        val jsonParser by lazy {
            JsonParser()
        }
        
        val TM_DATE_FORMAT = SimpleDateFormat("yyyy MMM dd", Locale.US)
    }
}
