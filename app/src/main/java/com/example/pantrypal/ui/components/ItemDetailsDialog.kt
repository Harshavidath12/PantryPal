package com.example.pantrypal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.pantrypal.R
import com.example.pantrypal.data.model.PantryItem

@Composable
fun ItemDetailsDialog(
    item: PantryItem?,
    onDismiss: () -> Unit,
    onMarkConsumed: (String) -> Unit,
    onAddToRestock: (String) -> Unit,
    onDonate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    if (item == null) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header (Title & Close Button)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Item Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color(0xFF1E293B),
                        modifier = Modifier.align(Alignment.Center)
                    )
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp)
                            .background(Color(0xFFE8F5E9), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF2D5A47),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Image with Badge
                Box(modifier = Modifier.size(140.dp)) {
                    AsyncImage(
                        model = item.imageUrl ?: item.iconResId ?: R.drawable.ic_food_yogurt,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                    
                    // Small white badge in bottom right corner
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .size(28.dp)
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pantry),
                            contentDescription = "Pantry Icon",
                            tint = Color(0xFF2D5A47),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title & Category
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color(0xFF1E293B),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = item.category,
                    color = Color(0xFF64748B),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Info Box (Owner, Freshness, Progress Bar)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFE8F5E9), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    // Row 1: Owner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = "Owner",
                            tint = Color(0xFF2D5A47),
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF2D5A47), CircleShape)
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Owner", color = Color(0xFF475569), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text(item.ownerName, fontWeight = FontWeight.Medium, color = Color(0xFF1E293B), fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Row 2: Freshness
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_sync), // Placeholder for clock
                            contentDescription = "Freshness",
                            tint = Color(0xFF2D5A47),
                            modifier = Modifier
                                .size(24.dp)
                                .background(Color(0xFF2D5A47), CircleShape)
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Freshness", color = Color(0xFF475569), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color(0xFFDCECE1), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(modifier = Modifier.size(6.dp).background(Color(0xFFD97706), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(item.expiryText, color = Color(0xFF2D5A47), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Progress Bar
                    LinearProgressIndicator(
                        progress = { item.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2D5A47),
                        trackColor = Color(0xFFDCECE1)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Button(
                    onClick = { onMarkConsumed(item.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2D5A47)),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mark as Consumed", color = Color.White)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onAddToRestock(item.id) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDCECE1)),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(painter = painterResource(id = R.drawable.ic_cart_add), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color(0xFF1E293B))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Add to Restock List", color = Color(0xFF1E293B))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Row of 2 buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { onDonate(item.id) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)), // Placeholder pastel
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_sparkles), contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF475569))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Donate", color = Color(0xFF475569), fontSize = 13.sp)
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Button(
                        onClick = { onEdit(item.id) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF1F5F9)),
                        shape = RoundedCornerShape(50)
                    ) {
                        Icon(painter = painterResource(id = R.drawable.ic_more_vert), contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF475569)) // Pencil alternative
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", color = Color(0xFF475569), fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Delete Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onDelete(item.id) }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Delete Item",
                        color = Color(0xFFDC2626),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ItemDetailsDialogPreview() {
    // Mock sample item matching the design
    val sampleItem = PantryItem(
        id = "1",
        title = "Fresh Whole Milk (2L)",
        category = "Dairy & Refrigerated",
        ownerName = "Tharushi (Shared)",
        expiryText = "Expires in 2 Days",
        progress = 0.65f,
        imageUrl = null
    )

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFE8F5E9) // Subtle green backdrop to simulate the blurred dashboard
    ) {
        ItemDetailsDialog(
            item = sampleItem,
            onDismiss = {},
            onMarkConsumed = {},
            onAddToRestock = {},
            onDonate = {},
            onEdit = {},
            onDelete = {}
        )
    }
}