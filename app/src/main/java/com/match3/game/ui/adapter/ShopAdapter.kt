package com.match3.game.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.match3.game.databinding.ItemShopBinding
import com.match3.game.ui.viewmodel.ShopItem
import com.match3.game.ui.viewmodel.ShopItemType

class ShopAdapter(
    private val onBuyClick: (ShopItem) -> Unit
) : ListAdapter<ShopItem, ShopAdapter.ShopViewHolder>(ShopDiffCallback()) {

    var playerWallet: Int = 0
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val binding = ItemShopBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ShopViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ShopViewHolder(
        private val binding: ItemShopBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ShopItem) {
            binding.itemEmoji.text = item.emoji
            binding.itemName.text = item.name
            binding.itemDescription.text = item.description
            binding.buyButton.text = "💰 ${item.cost}"

            val canAfford = playerWallet >= item.cost
            binding.buyButton.isEnabled = canAfford
            binding.buyButton.alpha = if (canAfford) 1f else 0.5f

            if (item.type == ShopItemType.PERK && item.owned > 0) {
                binding.itemOwned.visibility = View.VISIBLE
                binding.itemOwned.text = "Owned: ${item.owned}"
            } else {
                binding.itemOwned.visibility = View.GONE
            }

            binding.buyButton.setOnClickListener {
                if (canAfford) {
                    onBuyClick(item)
                }
            }
        }
    }

    class ShopDiffCallback : DiffUtil.ItemCallback<ShopItem>() {
        override fun areItemsTheSame(oldItem: ShopItem, newItem: ShopItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ShopItem, newItem: ShopItem): Boolean {
            return oldItem == newItem
        }
    }
}
