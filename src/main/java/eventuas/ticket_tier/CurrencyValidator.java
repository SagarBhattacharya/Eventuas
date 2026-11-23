package eventuas.ticket_tier;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;
import java.util.Locale;

public class CurrencyValidator implements ConstraintValidator<ValidCurrency, String> {
	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.isBlank()) {
			return true; // @NotBlank handles null/blank separately
		}
		try {
			Currency.getInstance(value.trim().toUpperCase(Locale.ROOT));
			return true;
		} catch (IllegalArgumentException _) {
			return false;
		}
	}
}