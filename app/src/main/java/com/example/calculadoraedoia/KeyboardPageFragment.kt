package com.example.calculadoraedoia

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

class KeyboardPageFragment : Fragment() {

    interface KeyClickListener {
        fun onKeyClicked(id: Int)
    }

    private var listener: KeyClickListener? = null

    companion object {
        private const val ARG_LAYOUT = "layoutRes"

        fun newInstance(layoutRes: Int): KeyboardPageFragment {
            val f = KeyboardPageFragment()
            f.arguments = Bundle().apply { putInt(ARG_LAYOUT, layoutRes) }
            return f
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? KeyClickListener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val layoutRes = requireArguments().getInt(ARG_LAYOUT)
        return inflater.inflate(layoutRes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindButtons(view)
    }

    private fun bindButtons(root: View) {
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                bindButtons(root.getChildAt(i))
            }
        } else if (root is Button) {
            root.setOnClickListener { listener?.onKeyClicked(root.id) }
        }
    }
}
