package com.example.calculadoraedoia

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class KeyboardPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val pages = listOf(
        "123" to R.layout.keyboard_tab_basic,
        "f(x)" to R.layout.keyboard_tab_funcs,
        "EDO" to R.layout.keyboard_tab_calculus
    )

    fun title(pos: Int) = pages[pos].first

    override fun getItemCount(): Int = pages.size

    override fun createFragment(position: Int): Fragment =
        KeyboardPageFragment.newInstance(pages[position].second)
}
