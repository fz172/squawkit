import { Ajv, type ValidateFunction } from "ajv";

import { AiError } from "../errors.js";
import {
  AiOutputParseError,
  type AiGenerateRequest,
  type AiProvider,
  type AiUsage,
  type JsonSchema,
} from "./types.js";

export type ValidatedResponse = {
  json: unknown;
  /** Summed over every attempt, including the failed one. */
  usage: AiUsage;
  attempts: number;
};

const ajv = new Ajv({ allErrors: true, strict: false });
const validators = new WeakMap<JsonSchema, ValidateFunction>();

/**
 * Calls the provider and checks the output against the request's schema, whatever the provider
 * promised. A failure is retried once with the validation error appended; a second failure is
 * `invalid_output` (PRD §9.4, "valid output after at most one retry").
 */
export async function generateValidated(
  provider: AiProvider,
  req: AiGenerateRequest,
): Promise<ValidatedResponse> {
  const validate = validatorFor(req.schema);
  let spent: AiUsage = { inputTokens: 0, outputTokens: 0, costMicros: 0 };
  let problem = "";

  for (let attempt = 1; attempt <= 2; attempt++) {
    const parts =
      attempt === 1
        ? req.parts
        : [
            ...req.parts,
            {
              text:
                `Your previous answer was rejected: ${problem}. ` +
                "Answer again with JSON that matches the schema exactly.",
            },
          ];
    try {
      const response = await provider.generate({ ...req, parts });
      spent = add(spent, response.usage);
      if (validate(response.json)) return { json: response.json, usage: spent, attempts: attempt };
      problem = ajv.errorsText(validate.errors, { separator: "; " });
    } catch (e) {
      if (e instanceof AiOutputParseError) {
        spent = add(spent, e.usage);
        problem = e.message;
      } else if (e instanceof AiError) {
        throw new AiError(e.code, e.detail, { cause: e.cause, usage: add(spent, e.usage) });
      } else {
        throw e;
      }
    }
  }
  throw new AiError("invalid_output", `${provider.id}: ${problem}`, { usage: spent });
}

function validatorFor(schema: JsonSchema): ValidateFunction {
  let validate = validators.get(schema);
  if (!validate) {
    validate = ajv.compile(schema);
    validators.set(schema, validate);
  }
  return validate;
}

function add(a: AiUsage, b: AiUsage | undefined): AiUsage {
  if (!b) return a;
  return {
    inputTokens: a.inputTokens + b.inputTokens,
    outputTokens: a.outputTokens + b.outputTokens,
    costMicros: a.costMicros + b.costMicros,
  };
}
