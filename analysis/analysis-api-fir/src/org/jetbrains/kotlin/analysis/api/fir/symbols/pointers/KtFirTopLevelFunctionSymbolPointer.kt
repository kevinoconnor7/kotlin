/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.symbols.pointers

import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.analysis.api.symbols.KtFunctionSymbol
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.name.CallableId

internal class KtFirTopLevelFunctionSymbolPointer(
    callableId: CallableId,
    private val signature: FirCallableSignature,
) : KtTopLevelCallableSymbolPointer<KtFunctionSymbol>(callableId) {
    override fun KtFirAnalysisSession.chooseCandidateAndCreateSymbol(
        candidates: Collection<FirCallableSymbol<*>>,
        firSession: FirSession
    ): KtFunctionSymbol? {
        val firFunction = candidates.findDeclarationWithSignatureBySymbols<FirSimpleFunction>(signature) ?: return null
        return firSymbolBuilder.functionLikeBuilder.buildFunctionSymbol(firFunction.symbol)
    }
}
