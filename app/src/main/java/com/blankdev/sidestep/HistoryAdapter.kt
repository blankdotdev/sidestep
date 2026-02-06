package com.blankdev.sidestep

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class HistoryAdapter(
    private val onItemClick: (HistoryManager.HistoryEntry) -> Unit,
    private val onMenuClick: (View, HistoryManager.HistoryEntry) -> Unit,
    private val themeColorProvider: (Int) -> Int,
    private val rippleDrawableProvider: () -> Drawable,
    private val dpProvider: (Int) -> Int,
    private val titleProvider: (HistoryManager.HistoryEntry) -> String,
    private val subtitleProvider: (HistoryManager.HistoryEntry) -> String
) : ListAdapter<HistoryManager.HistoryEntry, HistoryAdapter.ViewHolder>(
    object : DiffUtil.ItemCallback<HistoryManager.HistoryEntry>() {
        override fun areItemsTheSame(oldItem: HistoryManager.HistoryEntry, newItem: HistoryManager.HistoryEntry): Boolean {
            return oldItem.originalUrl == newItem.originalUrl && oldItem.timestamp == newItem.timestamp
        }

        override fun areContentsTheSame(oldItem: HistoryManager.HistoryEntry, newItem: HistoryManager.HistoryEntry): Boolean {
            return oldItem == newItem
        }
    }
) {
    companion object {
        private const val CONTAINER_HORIZONTAL_PADDING_DP = 8
        private const val CONTAINER_VERTICAL_PADDING_DP = 10
        private const val CONTAINER_END_PADDING_DP = 4
        private const val CONTENT_PADDING_DP = 8
        private const val IMAGE_SIZE_DP = 40
        private const val IMAGE_CARD_SIZE_DP = 56
        private const val IMAGE_CARD_MARGIN_END_DP = 16
        private const val CARD_CORNER_RADIUS = 16f
        private const val MENU_BUTTON_PADDING_DP = 12
        private const val TITLE_TEXT_SIZE_SP = 16f
        private const val SUBTITLE_TEXT_SIZE_SP = 14f
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val itemContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpProvider(CONTAINER_HORIZONTAL_PADDING_DP), dpProvider(CONTAINER_VERTICAL_PADDING_DP), dpProvider(CONTAINER_END_PADDING_DP), dpProvider(CONTAINER_VERTICAL_PADDING_DP))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
        }
        return ViewHolder(itemContainer)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        val context = holder.view.context
        val container = holder.view as LinearLayout
        container.removeAllViews()

        val contentUrl = entry.unshortenedUrl ?: entry.originalUrl
        val isTwitter = UrlCleaner.isTwitterOrXUrl(contentUrl)
        val isTikTok = UrlCleaner.isTikTokUrl(contentUrl)
        val isReddit = UrlCleaner.isRedditUrl(contentUrl)
        val isYouTube = UrlCleaner.isYouTubeUrl(contentUrl)
        val isMedium = UrlCleaner.isMediumUrl(contentUrl)
        val isImdb = UrlCleaner.isImdbUrl(contentUrl)
        val isWikipedia = UrlCleaner.isWikipediaUrl(contentUrl)
        val isGoodreads = UrlCleaner.isGoodreadsUrl(contentUrl)
        val isGenius = UrlCleaner.isGeniusUrl(contentUrl)
        val isGitHub = UrlCleaner.isGitHubUrl(contentUrl)
        val isStackOverflow = UrlCleaner.isStackOverflowUrl(contentUrl)
        val isNYTimes = contentUrl.contains("nytimes.com")
        val isInstagram = contentUrl.contains("instagram.com")
        val isTumblr = UrlCleaner.isTumblrUrl(contentUrl)
        val isImgur = UrlCleaner.isImgurUrl(contentUrl)
        val isUrbanDictionary = UrlCleaner.isUrbanDictionaryUrl(contentUrl)
        val isGoogleMaps = UrlCleaner.isGoogleMapsUrl(contentUrl)

        val contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.CENTER_VERTICAL
            background = rippleDrawableProvider()
            setPadding(dpProvider(CONTENT_PADDING_DP), dpProvider(CONTENT_PADDING_DP), dpProvider(CONTENT_PADDING_DP), dpProvider(CONTENT_PADDING_DP))
            setOnClickListener { onItemClick(entry) }
        }

        val displayImage = when {
            isTwitter -> R.drawable.ic_x_logo
            isTikTok -> R.drawable.ic_tiktok_logo
            isReddit -> R.drawable.ic_reddit_logo
            isYouTube -> R.drawable.ic_youtube_logo
            isMedium -> R.drawable.ic_medium_logo
            isImdb -> R.drawable.ic_imdb_logo
            isWikipedia -> R.drawable.ic_wikipedia_logo
            isGoodreads -> R.drawable.ic_goodreads_logo
            isGenius -> R.drawable.ic_genius_logo
            isGitHub -> R.drawable.ic_github_logo
            isStackOverflow -> R.drawable.ic_stackoverflow_logo
            isNYTimes -> R.drawable.ic_nytimes_logo
            isInstagram -> R.drawable.ic_instagram_logo
            isTumblr -> R.drawable.ic_tumblr_logo
            isImgur -> R.drawable.ic_imgur_logo
            isUrbanDictionary -> R.drawable.ic_urbandictionary_logo
            isGoogleMaps -> R.drawable.ic_google_maps_logo
            else -> R.drawable.ic_generic_link
        }

        val imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(dpProvider(IMAGE_SIZE_DP), dpProvider(IMAGE_SIZE_DP)).apply { gravity = Gravity.CENTER }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(displayImage)
            setColorFilter(themeColorProvider(com.google.android.material.R.attr.colorOnSurface))
        }

        val imageCard = CardView(context).apply {
            radius = CARD_CORNER_RADIUS
            cardElevation = 0f
            setCardBackgroundColor(Color.TRANSPARENT)
            layoutParams = LinearLayout.LayoutParams(dpProvider(IMAGE_CARD_SIZE_DP), dpProvider(IMAGE_CARD_SIZE_DP)).apply { marginEnd = dpProvider(IMAGE_CARD_MARGIN_END_DP) }
            addView(imageView)
        }
        contentContainer.addView(imageCard)

        val textLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val displayTitle = titleProvider(entry)
        val titleText = TextView(context).apply {
            if (!displayTitle.isBlank()) {
                text = displayTitle
                visibility = View.VISIBLE
            } else {
                text = ""
                visibility = View.GONE
            }
            textSize = TITLE_TEXT_SIZE_SP
            setTypeface(null, Typeface.BOLD)
            setTextColor(themeColorProvider(android.R.attr.textColorPrimary))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        textLayout.addView(titleText)

        val subtitleText = TextView(context).apply {
            text = subtitleProvider(entry)
            textSize = SUBTITLE_TEXT_SIZE_SP
            setTextColor(themeColorProvider(android.R.attr.textColorSecondary))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.MIDDLE
        }
        textLayout.addView(subtitleText)
        contentContainer.addView(textLayout)
        container.addView(contentContainer)

        val menuButton = android.widget.ImageButton(context).apply {
            setImageResource(R.drawable.ic_more_vert)
            background = rippleDrawableProvider()
            setPadding(dpProvider(MENU_BUTTON_PADDING_DP), dpProvider(MENU_BUTTON_PADDING_DP), dpProvider(MENU_BUTTON_PADDING_DP), dpProvider(MENU_BUTTON_PADDING_DP))
            setColorFilter(themeColorProvider(android.R.attr.textColorSecondary))
            setOnClickListener { onMenuClick(it, entry) }
        }
        container.addView(menuButton)
    }
}
