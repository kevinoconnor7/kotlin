/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.plugin.generators

import org.jetbrains.kotlin.descriptors.EffectiveVisibility
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.declarations.FirResolvePhase
import org.jetbrains.kotlin.fir.declarations.builder.buildSimpleFunction
import org.jetbrains.kotlin.fir.declarations.builder.buildValueParameter
import org.jetbrains.kotlin.fir.declarations.impl.FirResolvedDeclarationStatusImpl
import org.jetbrains.kotlin.fir.declarations.origin
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirDeclarationPredicateRegistrar
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.extensions.predicate.LookupPredicate
import org.jetbrains.kotlin.fir.extensions.predicateBasedProvider
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.plugin.fqn
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirRegularClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.builder.buildResolvedTypeRef
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name

/*
 * For each class with @Serializable annotation generates method serializeClassName(x: ClassName)
 *   in each class annotated with @CoreSerializer
 */
class MembersOfSerializerGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {
    companion object {
        private val SERIALIZABLE_PREDICATE = LookupPredicate.create { annotated("MySerializable".fqn()) }
        private val CORE_SERIALIZER_PREDICATE = LookupPredicate.create { annotated("CoreSerializer".fqn()) }

        private val X_NAME = Name.identifier("x")
    }

    private val predicateBasedProvider = session.predicateBasedProvider
    private val matchedSerializableClasses by lazy {
        predicateBasedProvider.getSymbolsByPredicate(SERIALIZABLE_PREDICATE).filterIsInstance<FirRegularClassSymbol>()
    }
    private val serializableClassIds by lazy {
        matchedSerializableClasses.map { it.classId }
    }

    private val matchedCoreSerializerClasses by lazy {
        predicateBasedProvider.getSymbolsByPredicate(CORE_SERIALIZER_PREDICATE).filterIsInstance<FirRegularClassSymbol>()
    }

    private val serializeMethodNames by lazy {
        serializableClassIds.associateBy { Name.identifier("serialize${it.shortClassName.identifier}") }
    }

    override fun generateFunctions(callableId: CallableId, context: MemberGenerationContext?): List<FirNamedFunctionSymbol> {
        val argumentClassId = serializeMethodNames[callableId.callableName] ?: return emptyList()
        val dispatchReceiverClassId = callableId.classId ?: return emptyList()
        val function = buildSimpleFunction {
            moduleData = session.moduleData
            resolvePhase = FirResolvePhase.BODY_RESOLVE
            origin = Key.origin
            status = FirResolvedDeclarationStatusImpl(Visibilities.Public, Modality.FINAL, EffectiveVisibility.Public)
            returnTypeRef = session.builtinTypes.unitType
            dispatchReceiverType = dispatchReceiverClassId.toSimpleConeType()
            symbol = FirNamedFunctionSymbol(callableId)
            valueParameters += buildValueParameter {
                moduleData = session.moduleData
                containingFunctionSymbol = this@buildSimpleFunction.symbol
                resolvePhase = FirResolvePhase.BODY_RESOLVE
                origin = Key.origin
                returnTypeRef = buildResolvedTypeRef {
                    type = argumentClassId.toSimpleConeType()
                }
                name = X_NAME
                symbol = FirValueParameterSymbol(name)
                isCrossinline = false
                isNoinline = false
                isVararg = false
            }
            body = buildBlock {}
                .apply { replaceTypeRef(session.builtinTypes.unitType) }
            name = callableId.callableName
        }
        return listOf(function.symbol)
    }

    override fun getCallableNamesForClass(classSymbol: FirClassSymbol<*>): Set<Name> {
        return when (classSymbol) {
            in matchedCoreSerializerClasses -> serializeMethodNames.keys
            else -> emptySet()
        }
    }

    override fun FirDeclarationPredicateRegistrar.registerPredicates() {
        register(SERIALIZABLE_PREDICATE, CORE_SERIALIZER_PREDICATE)
    }

    object Key : GeneratedDeclarationKey()
}
