LICENSE_IGNORE := -ignore 'build/**' -ignore '.gradle/**' -ignore '.kotlin/**' -ignore '.idea/**'

license-add:
	addlicense -c "Schwarz Digits Cloud GmbH & Co. KG" -l apache $(LICENSE_IGNORE) .

license-check:
	addlicense -check -c "Schwarz Digits Cloud GmbH & Co. KG" -l apache $(LICENSE_IGNORE) .
