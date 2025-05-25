package com.yasis.expensetracker

import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.yasis.expensetracker.models.Category
import com.yasis.expensetracker.models.ExpenseEntry
import com.yasis.expensetracker.services.FirestoreService
import com.yasis.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 🔐 Session check
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContent {
            ExpenseTrackerTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    var categoryName by remember { mutableStateOf("") }
    var categoryColor by remember { mutableStateOf("#FFFFFF") }
    var categories by remember { mutableStateOf(listOf<Category>()) }

    var expenseDescription by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var expenses by remember { mutableStateOf(listOf<ExpenseEntry>()) }

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var photoBitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }

    var isLoading by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedPhotoUri = uri
        uri?.let {
            val source = ImageDecoder.createSource(context.contentResolver, it)
            photoBitmap = ImageDecoder.decodeBitmap(source).asImageBitmap()
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("Manage Categories", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = categoryName, onValueChange = { categoryName = it }, label = { Text("Category Name") })
        OutlinedTextField(value = categoryColor, onValueChange = { categoryColor = it }, label = { Text("Color") })
        Button(onClick = {
            if (categoryName.isNotEmpty()) {
                isLoading = true
                FirestoreService.addCategory(Category(name = categoryName, color = categoryColor)) {
                    if (it) {
                        Toast.makeText(context, "Category added!", Toast.LENGTH_SHORT).show()
                        FirestoreService.getCategories { categories = it }
                    } else {
                        Toast.makeText(context, "Failed to add category.", Toast.LENGTH_SHORT).show()
                    }
                    isLoading = false
                }
                categoryName = ""
                categoryColor = "#FFFFFF"
            }
        }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Add Category")
        }
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(categories) { category ->
                Text("${category.name} (${category.color})")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Log Expense", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = expenseDescription, onValueChange = { expenseDescription = it }, label = { Text("Description") })
        OutlinedTextField(value = expenseAmount, onValueChange = { expenseAmount = it }, label = { Text("Amount") })
        DropdownMenuCategorySelector(categories, selectedCategory) { selectedCategory = it }
        OutlinedButton(onClick = { photoPickerLauncher.launch("image/*") }) {
            Text("Select Photo")
        }
        selectedPhotoUri?.let {
            photoBitmap?.let { bmp ->
                Image(bmp, contentDescription = "Selected Photo", modifier = Modifier.size(100.dp))
            }
        }

        Button(onClick = {
            val amount = expenseAmount.toDoubleOrNull()
            if (expenseDescription.isNotEmpty() && amount != null && selectedCategory != null) {
                isLoading = true
                if (selectedPhotoUri != null) {
                    FirestoreService.uploadPhoto(selectedPhotoUri!!) { photoUrl ->
                        if (photoUrl != null) {
                            FirestoreService.addExpense(
                                ExpenseEntry(description = expenseDescription, amount = amount, categoryId = selectedCategory!!.id, photoUrl = photoUrl)
                            ) {
                                if (it) {
                                    Toast.makeText(context, "Expense added with photo!", Toast.LENGTH_SHORT).show()
                                    FirestoreService.getExpenses { expenses = it }
                                } else {
                                    Toast.makeText(context, "Failed to add expense.", Toast.LENGTH_SHORT).show()
                                }
                                isLoading = false
                            }
                        } else {
                            Toast.makeText(context, "Photo upload failed.", Toast.LENGTH_SHORT).show()
                            isLoading = false
                        }
                    }
                } else {
                    FirestoreService.addExpense(
                        ExpenseEntry(description = expenseDescription, amount = amount, categoryId = selectedCategory!!.id)
                    ) {
                        if (it) {
                            Toast.makeText(context, "Expense added!", Toast.LENGTH_SHORT).show()
                            FirestoreService.getExpenses { expenses = it }
                        } else {
                            Toast.makeText(context, "Failed to add expense.", Toast.LENGTH_SHORT).show()
                        }
                        isLoading = false
                    }
                }
                expenseDescription = ""
                expenseAmount = ""
                selectedCategory = null
                selectedPhotoUri = null
                photoBitmap = null
            } else {
                Toast.makeText(context, "Please fill all fields.", Toast.LENGTH_SHORT).show()
            }
        }, modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Add Expense")
        }

        LazyColumn {
            items(expenses) { expense ->
                val category = categories.find { it.id == expense.categoryId }
                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${expense.description}: R${expense.amount}", style = MaterialTheme.typography.titleMedium)
                        Text("Category: ${category?.name ?: "Unknown"}", style = MaterialTheme.typography.bodyMedium)
                        expense.photoUrl?.let { url ->
                            AsyncImage(
                                model = url,
                                contentDescription = "Expense Photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
        }

        // 🔐 Logout Button
        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            context.startActivity(Intent(context, LoginActivity::class.java))
        }, modifier = Modifier.padding(top = 16.dp)) {
            Text("Logout")
        }
    }

    LaunchedEffect(Unit) {
        FirestoreService.getCategories { categories = it }
        FirestoreService.getExpenses { expenses = it }
    }
}

@Composable
fun DropdownMenuCategorySelector(categories: List<Category>, selectedCategory: Category?, onCategorySelected: (Category) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedCategory?.name ?: "Select Category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category.name) }, onClick = {
                    onCategorySelected(category)
                    expanded = false
                })
            }
        }
    }
}
