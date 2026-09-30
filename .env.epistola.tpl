#
# SPDX-FileCopyrightText: 2026 INFO.nl
# SPDX-License-Identifier: EUPL-1.2+
#

# The Epistola settings for `./start-docker-compose.sh -E`, resolved from the 1Password vault on top of
# .env.tpl. Kept apart so that a stack started without -E, including the WireMock one, needs none of them.
EPISTOLA_CLIENT_MP_REST_URL=op://Dimpact/Epistola Client MP REST URL/credential
EPISTOLA_TENANT_ID=op://Dimpact/Epistola Tenant Id/credential
EPISTOLA_CATALOG_ID=op://Dimpact/Epistola Catalog Id/credential
EPISTOLA_CLIENT_API_KEY=op://Dimpact/Epistola Client API Key/credential
