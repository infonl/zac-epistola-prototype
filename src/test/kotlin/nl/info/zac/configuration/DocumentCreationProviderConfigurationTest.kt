/*
 * SPDX-FileCopyrightText: 2026 INFO.nl
 * SPDX-License-Identifier: EUPL-1.2+
 */
package nl.info.zac.configuration

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.checkUnnecessaryStub
import nl.info.zac.configuration.exception.InvalidDocumentCreationProviderConfigurationException
import nl.info.zac.documentcreation.model.DocumentCreationProvider
import java.time.Duration
import java.util.Optional

private fun configuration(
    provider: String? = null,
    smartDocumentsEnabled: Boolean? = null,
    epistolaRestUrl: String? = null,
    epistolaTenantId: String? = null,
    epistolaCatalogId: String? = null,
    epistolaApiKey: String? = null,
    epistolaGenerationTimeoutSeconds: Long = 60L
) = DocumentCreationProviderConfiguration(
    configuredProvider = Optional.ofNullable(provider),
    smartDocumentsEnabled = Optional.ofNullable(smartDocumentsEnabled),
    epistolaRestUrl = Optional.ofNullable(epistolaRestUrl),
    epistolaTenantId = Optional.ofNullable(epistolaTenantId),
    epistolaCatalogId = Optional.ofNullable(epistolaCatalogId),
    epistolaApiKey = Optional.ofNullable(epistolaApiKey),
    epistolaGenerationTimeoutSeconds = epistolaGenerationTimeoutSeconds
)

private fun epistolaConfiguration(
    smartDocumentsEnabled: Boolean? = null,
    epistolaRestUrl: String? = "https://epistola.example.com",
    epistolaTenantId: String? = "zac-gemeente",
    epistolaCatalogId: String? = "zac-catalogus",
    epistolaApiKey: String? = "fakeApiKey",
    epistolaGenerationTimeoutSeconds: Long = 60L
) = configuration(
    provider = "Epistola",
    smartDocumentsEnabled = smartDocumentsEnabled,
    epistolaRestUrl = epistolaRestUrl,
    epistolaTenantId = epistolaTenantId,
    epistolaCatalogId = epistolaCatalogId,
    epistolaApiKey = epistolaApiKey,
    epistolaGenerationTimeoutSeconds = epistolaGenerationTimeoutSeconds
)

class DocumentCreationProviderConfigurationTest : BehaviorSpec({
    afterEach { checkUnnecessaryStub() }

    given("an existing installation that only sets SMARTDOCUMENTS_ENABLED") {
        `when`("the flag is true") {
            val configuration = configuration(smartDocumentsEnabled = true)
            then("SmartDocuments stays the active provider and startup is accepted") {
                configuration.activeProvider shouldBe DocumentCreationProvider.SMARTDOCUMENTS
                shouldNotThrowAny { configuration.onStartup(Any()) }
            }
        }

        `when`("the flag is false") {
            val configuration = configuration(smartDocumentsEnabled = false)
            then("no document creation provider is active") {
                configuration.activeProvider shouldBe DocumentCreationProvider.NONE
                shouldNotThrowAny { configuration.onStartup(Any()) }
            }
        }
    }

    given("an installation that configures nothing at all") {
        val configuration = configuration()
        `when`("the configuration is validated on startup") {
            then("document creation is disabled and startup is accepted") {
                configuration.activeProvider shouldBe DocumentCreationProvider.NONE
                shouldNotThrowAny { configuration.onStartup(Any()) }
            }
        }
    }

    given("DOCUMENT_CREATION_PROVIDER set to Epistola with all Epistola settings present") {
        val configuration = epistolaConfiguration()
        `when`("the configuration is validated on startup") {
            then("Epistola is the active provider and startup is accepted") {
                configuration.activeProvider shouldBe DocumentCreationProvider.EPISTOLA
                shouldNotThrowAny { configuration.onStartup(Any()) }
            }
        }
    }

    given("Epistola selected with all its settings and a generation timeout of 45 seconds") {
        val configuration = epistolaConfiguration(epistolaGenerationTimeoutSeconds = 45L)

        `when`("the Epistola settings are produced") {
            val epistolaSettings = configuration.epistolaSettings()

            then("they carry the configured values, with the timeout as a duration") {
                epistolaSettings shouldBe EpistolaSettings(
                    restUrl = "https://epistola.example.com",
                    tenantId = "zac-gemeente",
                    catalogId = "zac-catalogus",
                    apiKey = "fakeApiKey",
                    generationTimeout = Duration.ofSeconds(45)
                )
            }

            and("printing them does not reveal the API key") {
                epistolaSettings.toString() shouldNotContain "fakeApiKey"
            }
        }
    }

    given("an installation that does not use Epistola") {
        val configuration = configuration(smartDocumentsEnabled = true)

        `when`("the Epistola settings are asked for anyway") {
            val invalidDocumentCreationProviderConfigurationException =
                shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                    configuration.epistolaSettings()
                }

            then("they are refused, naming what is missing") {
                invalidDocumentCreationProviderConfigurationException.message shouldContain "EPISTOLA_CLIENT_API_KEY"
            }
        }
    }

    given("DOCUMENT_CREATION_PROVIDER written in a different case") {
        `when`("the documented spelling 'SmartDocuments' is used") {
            then("it resolves to the SmartDocuments provider") {
                configuration(
                    provider = "SmartDocuments",
                    smartDocumentsEnabled = true
                ).activeProvider shouldBe DocumentCreationProvider.SMARTDOCUMENTS
            }
        }
        `when`("a lowercase 'none' is used") {
            then("it resolves to no provider") {
                configuration(provider = "none").activeProvider shouldBe DocumentCreationProvider.NONE
            }
        }
    }

    given("both providers configured at once") {
        val configuration = epistolaConfiguration(smartDocumentsEnabled = true)
        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }
            then("startup fails naming both variables and how to resolve the conflict") {
                exception.message!! shouldContain "DOCUMENT_CREATION_PROVIDER"
                exception.message!! shouldContain "SMARTDOCUMENTS_ENABLED"
                exception.message!! shouldContain "one document creation provider at a time"
            }
        }
    }

    given("SmartDocuments selected while SMARTDOCUMENTS_ENABLED is false") {
        val configuration = configuration(provider = "SmartDocuments", smartDocumentsEnabled = false)
        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }
            then("startup fails because the two settings contradict each other") {
                exception.message!! shouldContain "SMARTDOCUMENTS_ENABLED is 'false'"
            }
        }
    }

    given("SmartDocuments selected while SMARTDOCUMENTS_ENABLED is not set at all") {
        val configuration = configuration(provider = "SmartDocuments", smartDocumentsEnabled = null)
        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }
            then("startup fails because the SmartDocuments service would stay inert without the flag") {
                exception.message!! shouldContain "selects SmartDocuments"
                exception.message!! shouldContain "SMARTDOCUMENTS_ENABLED is '<not set>'"
                exception.message!! shouldContain "Set it to 'true'"
            }
        }
    }

    given("an unrecognised DOCUMENT_CREATION_PROVIDER value") {
        val configuration = configuration(provider = "Word")
        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }
            then("startup fails listing the supported values") {
                exception.message!! shouldContain "'Word'"
                exception.message!! shouldContain "SMARTDOCUMENTS"
                exception.message!! shouldContain "EPISTOLA"
                exception.message!! shouldContain "NONE"
            }
        }
    }

    given("Epistola selected without its required settings") {
        val configuration = configuration(
            provider = "Epistola",
            epistolaRestUrl = null,
            epistolaTenantId = "tenant",
            epistolaCatalogId = "catalogus",
            epistolaApiKey = ""
        )
        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }
            then("startup fails naming exactly the missing and blank variables") {
                exception.message!! shouldContain "EPISTOLA_CLIENT_MP_REST_URL"
                exception.message!! shouldContain "EPISTOLA_CLIENT_API_KEY"
                exception.message!!.contains("EPISTOLA_TENANT_ID") shouldBe false
            }
        }
    }

    given("Epistola selected without a catalog identifier") {
        val configuration = epistolaConfiguration(epistolaCatalogId = null)

        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }

            then("startup fails, because Epistola requires a catalog on every call it is used for") {
                exception.message!! shouldContain "EPISTOLA_CATALOG_ID"
            }
        }
    }

    given("Epistola selected with a tenant identifier that is not a slug") {
        val configuration = epistolaConfiguration(epistolaTenantId = "ZAC_Gemeente")

        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }

            then("startup fails rather than every Epistola call being rejected later") {
                exception.message!! shouldContain "EPISTOLA_TENANT_ID"
                exception.message!! shouldContain "ZAC_Gemeente"
            }
        }
    }

    given("Epistola selected with a tenant identifier shorter than Epistola accepts") {
        val configuration = epistolaConfiguration(epistolaTenantId = "ab")

        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }

            then("startup fails naming the accepted length") {
                exception.message!! shouldContain "3 to 63"
            }
        }
    }

    given("Epistola selected with a tenant identifier that carries trailing whitespace") {
        val configuration = epistolaConfiguration(epistolaTenantId = "zac-gemeente ")

        `when`("the configuration is validated on startup") {
            val invalidDocumentCreationProviderConfigurationException =
                shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                    configuration.onStartup(Any())
                }

            then("startup fails, because ZAC sends the identifier to Epistola exactly as it is configured") {
                invalidDocumentCreationProviderConfigurationException.message shouldContain "'zac-gemeente '"
            }
        }
    }

    given("Epistola selected with a catalog identifier that is not a slug") {
        val configuration = epistolaConfiguration(epistolaCatalogId = "ZAC/Catalogus")

        `when`("the configuration is validated on startup") {
            val exception = shouldThrow<InvalidDocumentCreationProviderConfigurationException> {
                configuration.onStartup(Any())
            }

            then("startup fails naming the accepted length, which is shorter than the tenant's") {
                exception.message!! shouldContain "EPISTOLA_CATALOG_ID"
                exception.message!! shouldContain "ZAC/Catalogus"
                exception.message!! shouldContain "3 to 50"
            }
        }
    }

    given("SmartDocuments selected with a tenant identifier that is not a slug") {
        val configuration = configuration(
            provider = "SmartDocuments",
            smartDocumentsEnabled = true,
            epistolaTenantId = "ZAC_Gemeente"
        )

        `when`("the configuration is validated on startup") {
            then("the unused Epistola setting is not held against it") {
                shouldNotThrowAny { configuration.onStartup(Any()) }
            }
        }
    }
})
