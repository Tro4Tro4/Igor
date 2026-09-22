package com.igor.fridge.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.igor.fridge.IgorApplication

/** Recupera l'Application dai CreationExtras dentro le factory dei ViewModel. */
fun CreationExtras.igorApplication(): IgorApplication =
    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as IgorApplication
