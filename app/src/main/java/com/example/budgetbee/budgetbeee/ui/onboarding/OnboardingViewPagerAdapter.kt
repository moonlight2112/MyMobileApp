package com.example.budgetbee.budgetbeee.ui.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.budgetbee.budgetbeee.R
import com.example.budgetbee.budgetbeee.databinding.ItemOnboardingBinding

class OnboardingViewPagerAdapter : RecyclerView.Adapter<OnboardingViewPagerAdapter.OnboardingViewHolder>() {

    private val onboardingItems = listOf(
        OnboardingItem(
            "Take Control of Your Money",
            "Track every transaction in one place-no more guessing where your money goes.",
            R.drawable.pngtree_mobile_expense_management_abstract_concept_vector_illustration_png_image_5912719
        ),
        OnboardingItem(
            "Budget Made Simple",
            "Set monthly budgets and get alerts before you overspend.",
            R.drawable.images
        ),
        OnboardingItem(
            "Insightful Analytics",
            "Get detailed insights into your financial health",
            R.drawable.istockphoto_1279952311_612x612
        )
    )

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OnboardingViewHolder {
        val binding = ItemOnboardingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OnboardingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OnboardingViewHolder, position: Int) {
        holder.bind(onboardingItems[position])
    }

    override fun getItemCount() = onboardingItems.size

    class OnboardingViewHolder(private val binding: ItemOnboardingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: OnboardingItem) {
            binding.textTitle.text = item.title
            binding.textDescription.text = item.description
            binding.imageOnboarding.setImageResource(item.imageResId)
        }
    }

    data class OnboardingItem(
        val title: String,
        val description: String,
        val imageResId: Int
    )
}