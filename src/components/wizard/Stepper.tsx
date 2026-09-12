import { STEPS, type StepId } from "../../types/wizard";

interface StepperProps {
  currentStep: StepId;
}

export default function Stepper({ currentStep }: StepperProps) {
  return (
    <ol className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-line pb-5">
      {STEPS.map((step, index) => {
        const isActive = step.id === currentStep;
        const isDone = step.id < currentStep;

        return (
          <li key={step.id} className="flex items-center gap-2.5">
            <span
              className={[
                "flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-xs font-semibold transition-all duration-300",
                isActive
                  ? "bg-accent-600 text-white shadow-md shadow-accent-600/30"
                  : isDone
                    ? "bg-success-500 text-white"
                    : "border-2 border-ink-400/30 text-ink-400",
              ].join(" ")}
            >
              {isDone ? (
                <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
                  <path d="M2.5 6.2L4.8 8.5L9.5 3.5" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
                </svg>
              ) : (
                step.id
              )}
            </span>
            <span
              className={[
                "text-sm transition-colors duration-200",
                isActive ? "font-medium text-ink-900" : isDone ? "text-success-500" : "text-ink-400",
              ].join(" ")}
            >
              {step.label}
            </span>
            {index < STEPS.length - 1 && (
              <div className="ml-2 hidden h-px w-4 bg-line sm:block" />
            )}
          </li>
        );
      })}
    </ol>
  );
}
