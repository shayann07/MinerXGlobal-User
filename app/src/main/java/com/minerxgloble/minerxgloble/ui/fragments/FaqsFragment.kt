package com.minerxgloble.minerxgloble.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.text.HtmlCompat
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.minerxgloble.minerxgloble.R
import com.minerxgloble.minerxgloble.databinding.FragmentFaqsBinding

class FaqsFragment : BaseFragment() {

    private var _binding: FragmentFaqsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFaqsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupDrawerTrigger(view)

        // Apply HTML-formatted strings so <b> and <br/> render correctly
        binding.a1.text = fromHtml(getString(R.string.faq_intro))
        binding.a2.text = fromHtml(getString(R.string.faq_packages_rich))
        binding.a3.text = fromHtml(getString(R.string.faq_daily_roi))
        binding.a4.text = fromHtml(getString(R.string.faq_direct_level_income))
        binding.a5.text = fromHtml(getString(R.string.faq_ranks_rewards))
        binding.a6.text = fromHtml(getString(R.string.faq_lucky_draw))
        binding.a7.text = fromHtml(getString(R.string.faq_tokens))
        binding.a8.text = fromHtml(getString(R.string.faq_withdrawals))
        binding.a9.text = fromHtml(getString(R.string.faq_support))

        // Accordion toggles
        setAccordion(binding.q1, binding.a1, binding.ic1)
        setAccordion(binding.q2, binding.a2, binding.ic2)
        setAccordion(binding.q3, binding.a3, binding.ic3)
        setAccordion(binding.q4, binding.a4, binding.ic4)
        setAccordion(binding.q5, binding.a5, binding.ic5)
        setAccordion(binding.q6, binding.a6, binding.ic6)
        setAccordion(binding.q7, binding.a7, binding.ic7)
        setAccordion(binding.q8, binding.a8, binding.ic8)
        setAccordion(binding.q9, binding.a9, binding.ic9)
    }

    private fun fromHtml(src: String) =
        HtmlCompat.fromHtml(src, HtmlCompat.FROM_HTML_MODE_LEGACY)

    private fun setAccordion(question: View, answer: TextView, chevron: ImageView) {
        question.setOnClickListener {
            val parent = binding.faqList
            TransitionManager.beginDelayedTransition(parent, AutoTransition().apply {
                duration = 180
            })
            val show = answer.visibility != View.VISIBLE
            answer.visibility = if (show) View.VISIBLE else View.GONE
            chevron.rotation = if (show) 180f else 0f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
